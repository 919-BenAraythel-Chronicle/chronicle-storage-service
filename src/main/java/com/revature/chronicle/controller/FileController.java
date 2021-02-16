package com.revature.chronicle.controller;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.revature.chronicle.models.Media;
import com.revature.chronicle.models.Note;
import com.revature.chronicle.models.Tag;
import com.revature.chronicle.models.Video;
import com.revature.chronicle.services.NoteService;
import com.revature.chronicle.services.S3FileService;
import com.revature.chronicle.services.TagService;
import com.revature.chronicle.services.VideoService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Log4j2
@RestController
@RequestMapping(path="/file", method = {RequestMethod.GET, RequestMethod.POST})
public class FileUploadController {

    private final S3FileService s3FileService;
    private final TagService tagService;
    private final VideoService videoService;
    private final NoteService noteService;

    @Autowired
    public FileUploadController(S3FileService service, VideoService videoService, NoteService noteService, TagService tagService) {
        this.s3FileService = service;
        this.videoService = videoService;
        this.noteService = noteService;
        this.tagService = tagService;
    }

    /**
     * Takes in a 'json' and 'file' pair, maps them to the proper Media object before saving the 'file' to the s3 bucket and persisting the media object to the database.
     * @param json A JSON object containing meta data for the file
     * @param file The multipart file to be saved to the s3 bucket using S3FileService
     * @return An HTTP Status code whether the media sent back is of the proper type or not
     * @throws IOException
     */
    @PostMapping(path = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> uploadFile(@RequestParam("json") String json,
                                             @RequestParam("file") MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        log.info("Processing the following file data:" + content);

        ObjectMapper mapper = new ObjectMapper();
        //HTTP Response to send back
        String responseBody = "Successfully uploaded file!";

        //File to upload to S3 and save to database
        Media newFile = null;
        String fileType = "";

        //Determine what type of file has been uploaded: [VIDEO or TEXT] and create the appropriate model object
        try {
            if (Objects.requireNonNull(file.getContentType()).contains("text") || file.getContentType().contains("pdf")) {
                newFile = mapper.readValue(json, Note.class);
                fileType = "note";
            } else if (file.getContentType().contains("video")) {
                newFile = mapper.readValue(json, Video.class);
                fileType = "video";
            } else {
                responseBody = "Unsupported file type. Please upload either a video or a text file.";
                return new ResponseEntity<>(mapper.writeValueAsString(responseBody), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
            log.info(fileType.toUpperCase() + ": " + newFile.toString());

        } catch (MismatchedInputException e) {
            log.warn(e.getMessage());
        }
        File compiledFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
        compiledFile.deleteOnExit();

        //Save file to S3 bucket and persist newFile object to the database
        try {
            file.transferTo(compiledFile.getAbsoluteFile());
            log.debug(compiledFile);
            String s3URL = s3FileService.uploadFile(compiledFile);

            log.info("The generated S3 URL: " + s3URL);

            //Insert s3URL and json form data into the database
            if (newFile != null) {
                newFile.setUrl(s3URL);
                saveToDatabase(newFile, fileType);
            }

        } catch (AmazonS3Exception e) {
            e.printStackTrace();
            log.error("Unable to access the AWS S3 bucket!");
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Failed to write to file");
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.error("Service was interrupted!");
        } finally {
            if(compiledFile.delete())
                log.info("Temporary compiled file successfully deleted.");
        }
        return new ResponseEntity<>(mapper.writeValueAsString(responseBody), HttpStatus.OK);
    }

    /**
     * Ensures the media file gets saved to the proper database
     * @param media The file being saved to the Notes or Videos database
     * @param mediaType A string representation of the media type for the conditional statement
     */
    private void saveToDatabase (Media media, String mediaType){
        log.info("Saving the " + mediaType.toUpperCase() + ": " + media.getTitle() + " to the database!");
        media.setTags(configureTags(media.getTags()));
        if (mediaType.equalsIgnoreCase("note")) {
            noteService.save((Note)media);
        } else if (mediaType.equalsIgnoreCase("video")) {
            videoService.save((Video)media);
        }
    }
    /**
     * Persists tags to the database and ensures no duplicates
     * @param tags A list of tags to be compared to the Tags table
     * @return A list of properly configured tags
     */
    private List<Tag> configureTags(List<Tag> tags) {
        List<Tag> configuredTags = new ArrayList<>();
        for (Tag tag: tags) {
            Tag tempTag = new Tag();
            tempTag.setTagID(tag.getTagID());
            tempTag.setType(tag.getType());
            tempTag.setValue(tag.getValue());
            if (tag.getTagID() == 0) {
                tagService.save(tempTag);
                tempTag = tagService.findByValue(tempTag.getValue());
            }
            configuredTags.add(tempTag);
        }
        return configuredTags;
    }
}

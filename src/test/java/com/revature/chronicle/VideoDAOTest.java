package com.revature.chronicle;

import com.revature.chronicle.daos.VideoDAO;
import com.revature.chronicle.daos.VideoDAOImpl;
import com.revature.chronicle.models.Video;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import java.util.List;

@SpringBootTest
public class VideoDAOTest {
    VideoDAO videoDAO = new VideoDAOImpl();

    @Test
    public void getVideosTest() {
        List<Video> list = videoDAO.getVideos();
        Assert.notNull(list, "assertion failed: list returned null");
    }

/*
    @Test
    public void getVideoByIdTest(){
        Video video = new Video();
        int vidId = 1;
        String vidUrl = "vid.Url";
        String vidDesc = "video description";
        video.setVideoID(vidId);
        video.setUrl(vidUrl);
        video.setDescription(vidDesc);
        //video.setUser(); //how do I set/mock a user?
        //video.setVideo_tags();//do I set/mock the video_tags?
        videoDAO.addVideo(video);
        //if I'm just testing getVideoById do I need to make sure every other field in video is set?
        Assert.notNull(video, "assertion failed: getVideoById returned null"); //if the assertion fails throw a message
    }
*/

    @Test
    public void getVideoByIdTest(){
        Video video = videoDAO.getVideoById(1);
        Assert.notNull(video, "assertion failed: getVideoById returned null");
    }

    @Test
    public void getVideoByIdTestNull(){ //should I do null tests? is there a point?
        Video video = videoDAO.getVideoById(-1);
        Assert.isNull(video, "assertion failed: getVideoById returned not-null");
    }

    @Test
    public void addVideoTest(){
        Video video = new Video(); //should I set the model fields
        boolean addTest = videoDAO.addVideo(video);
        Assert.isTrue(addTest,"assertion failed: addTest is false");
    }

    @Test
    public void updateVideoTest(){
        Video video = new Video(); //should I set the model fields
        boolean updateTest = videoDAO.updateVideo(video);
        Assert.isTrue(updateTest,"assertions failed: updateVideo is false");
    }

    @Test
    public void deleteVideoTest(){
        boolean deleteTest = videoDAO.deleteVideoById(1);
        Assert.isTrue(deleteTest,"assertions failed: deleteVideo is false");
    }
}

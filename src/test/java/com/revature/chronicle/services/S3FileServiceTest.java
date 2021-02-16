package com.revature.chronicle.services;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;

import io.findify.s3mock.S3Mock;


public class S3FileServiceTest {
	
	public S3FileService s3FileService;
	public S3Mock s3Mock;
	public AmazonS3 s3ClientMock;
	
	@BeforeEach
	public void setup() throws IOException {
		s3FileService = new S3FileService();
        s3Mock = new S3Mock.Builder().withPort(8001).withInMemoryBackend().build();
        s3Mock.start();
        AwsClientBuilder.EndpointConfiguration endpoint = new AwsClientBuilder.EndpointConfiguration("http://localhost:8001", "us-east-1");
        System.out.println(endpoint.getServiceEndpoint());
        
        s3ClientMock = AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(endpoint)
                .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
                .build();

        s3FileService.setAwsClient(s3ClientMock);
        s3ClientMock.createBucket("test-bucket");
        s3FileService.setAwsBucket("test-bucket");
	}

    @Test
    public void uploadFileSuccessfullyAndReturnObjectURL() throws IOException, InterruptedException {
    	File file = File.createTempFile("temp", ".txt");
    	
        String result = s3FileService.uploadFile(file);
        String expected = s3ClientMock.getUrl("test-bucket", file.getName()).toString();

        Assert.assertEquals(expected, result);
        s3Mock.stop();
    }
    
    @Test
    public void downloadFileSuccessfully() throws InterruptedException, IOException {	
    	File file = File.createTempFile("temp", ".txt");
        s3FileService.uploadFile(file);
        
        //System.out.println(file.getName());
    	
    	String result = s3FileService.downloadFile(file.getName());
        
    	Assert.assertNotNull(result);
    	s3Mock.stop();
    }

    @Test
    public void getExistingObjectURL() throws IOException {
    	File file = File.createTempFile("temp", ".txt");

        String actual = s3FileService.getObjectUrl(file.getName());
        String expected = s3ClientMock.getUrl("test-bucket", file.getName()).toString();

        Assert.assertEquals(expected, actual);
        s3Mock.stop();
    }

    @Test
    public void uploadVideoFileSuccessfully() throws IOException {
        File file = File.createTempFile("temp", ".mp4");
        long contentLength = file.length();
        long partSize = 25 * 1024 * 1024; // 25MB part size

        List<PartETag> partETags = new ArrayList<PartETag>();

        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest("test-bucket", "temp");
        InitiateMultipartUploadResult initResponse = s3ClientMock.initiateMultipartUpload(initRequest);

        long filePosition = 0;
        for(int i = 1; filePosition < contentLength; i++) {
            partSize = Math.min(partSize, (contentLength-filePosition));

            UploadPartRequest uploadPartRequest = new UploadPartRequest()
                    .withBucketName("test-bucket")
                    .withKey("temp")
                    .withUploadId(initResponse.getUploadId())
                    .withPartNumber(i)
                    .withFileOffset(filePosition)
                    .withFile(file)
                    .withPartNumber((int) partSize);

            UploadPartResult uploadResult = s3ClientMock.uploadPart(uploadPartRequest);
            partETags.add(uploadResult.getPartETag());

            filePosition += partSize;
        }
        CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest("test-bucket", "temp",
                initResponse.getUploadId(), partETags);

        s3ClientMock.completeMultipartUpload(compRequest);

        String actual = s3FileService.uploadVideo(file);
        String expected = s3ClientMock.getUrl("test-bucket", file.getName()).toString();

        Assert.assertEquals(expected, actual);
        s3Mock.stop();
    }
}

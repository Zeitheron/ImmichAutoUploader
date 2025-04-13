package dev.zeith.immich.io;

import lombok.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;

@Getter
public class HTTPRequestMultipartBody
{
	private final byte[] body;
	
	@Setter
	private String boundary;
	
	private HTTPRequestMultipartBody(byte[] body, String boundary)
	{
		this.body = body;
		this.boundary = boundary;
	}
	
	public String getContentType()
	{
		return "multipart/form-data; boundary=" + this.getBoundary();
	}
	
	public static class Builder
	{
		@Getter
		@Setter
		public static class MultiPartRecord
		{
			private String fieldName;
			private String filename;
			private Object content;
		}
		
		List<MultiPartRecord> parts;
		
		public Builder()
		{
			this.parts = new ArrayList<>();
		}
		
		public Builder addPart(String fieldName, String fieldValue)
		{
			MultiPartRecord part = new MultiPartRecord();
			part.setFieldName(fieldName);
			part.setContent(fieldValue);
			this.parts.add(part);
			return this;
		}
		
		public Builder addPart(String fieldName, String fieldValue, String contentType)
		{
			MultiPartRecord part = new MultiPartRecord();
			part.setFieldName(fieldName);
			part.setContent(fieldValue);
			this.parts.add(part);
			return this;
		}
		
		public Builder addPart(String fieldName, Object fieldValue, String contentType, String fileName)
		{
			MultiPartRecord part = new MultiPartRecord();
			part.setFieldName(fieldName);
			part.setContent(fieldValue);
			part.setFilename(fileName);
			this.parts.add(part);
			return this;
		}
		
		public HTTPRequestMultipartBody build()
				throws IOException
		{
			String boundary = "------WebKitFormBoundary" + new BigInteger(128, new SecureRandom());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			for(MultiPartRecord record : parts)
			{
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append("--").append(boundary).append("\r\n").append("Content-Disposition: form-data; name=\"").append(record.getFieldName());
				if(record.getFilename() != null)
				{
					stringBuilder.append("\"; filename=\"").append(record.getFilename());
				}
				out.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
				out.write(("\"\r\n").getBytes(StandardCharsets.UTF_8));
				Object content = record.getContent();
				switch(content)
				{
					case String s ->
					{
						out.write(("\r\n").getBytes(StandardCharsets.UTF_8));
						out.write(s.getBytes(StandardCharsets.UTF_8));
					}
					case byte[] bytes1 ->
					{
						out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
						out.write(bytes1);
					}
					case File file ->
					{
						out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
						Files.copy(file.toPath(), out);
					}
					case null, default ->
					{
						out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
						ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
						objectOutputStream.writeObject(content);
						objectOutputStream.flush();
					}
				}
				out.write("\r\n".getBytes(StandardCharsets.UTF_8));
			}
			out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			
			return new HTTPRequestMultipartBody(out.toByteArray(), boundary);
		}
	}
}

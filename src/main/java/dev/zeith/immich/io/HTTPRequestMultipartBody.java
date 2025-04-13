package dev.zeith.immich;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;

public class HTTPRequestMultipartBody
{
	private final byte[] bytes;
	
	public String getBoundary()
	{
		return boundary;
	}
	
	public void setBoundary(String boundary)
	{
		this.boundary = boundary;
	}
	
	private String boundary;
	
	private HTTPRequestMultipartBody(byte[] bytes, String boundary)
	{
		this.bytes = bytes;
		this.boundary = boundary;
	}
	
	public String getContentType()
	{
		return "multipart/form-data; boundary=" + this.getBoundary();
	}
	
	public byte[] getBody()
	{
		return this.bytes;
	}
	
	public static class Builder
	{
		private final String DEFAULT_MIMETYPE = "text/plain";
		
		public static class MultiPartRecord
		{
			private String fieldName;
			private String filename;
			private Object content;
			
			public String getFieldName()
			{
				return fieldName;
			}
			
			public void setFieldName(String fieldName)
			{
				this.fieldName = fieldName;
			}
			
			public String getFilename()
			{
				return filename;
			}
			
			public void setFilename(String filename)
			{
				this.filename = filename;
			}
			
			public Object getContent()
			{
				return content;
			}
			
			public void setContent(Object content)
			{
				this.content = content;
			}
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
				stringBuilder.append("--" + boundary + "\r\n" + "Content-Disposition: form-data; name=\"" + record.getFieldName());
				if(record.getFilename() != null)
				{
					stringBuilder.append("\"; filename=\"" + record.getFilename());
				}
				out.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
				out.write(("\"\r\n").getBytes(StandardCharsets.UTF_8));
				Object content = record.getContent();
				if(content instanceof String)
				{
					out.write(("\r\n").getBytes(StandardCharsets.UTF_8));
					out.write(((String) content).getBytes(StandardCharsets.UTF_8));
				} else if(content instanceof byte[])
				{
					out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
					out.write((byte[]) content);
				} else if(content instanceof File)
				{
					out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
					Files.copy(((File) content).toPath(), out);
				} else
				{
					out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
					ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
					objectOutputStream.writeObject(content);
					objectOutputStream.flush();
				}
				out.write("\r\n".getBytes(StandardCharsets.UTF_8));
			}
			out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			
			return new HTTPRequestMultipartBody(out.toByteArray(), boundary);
		}
	}
}

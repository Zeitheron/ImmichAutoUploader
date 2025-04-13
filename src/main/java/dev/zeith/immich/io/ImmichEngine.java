package dev.zeith.immich;

import com.google.gson.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.*;

public class ImmichEngine
{
	private final HttpClient httpClient;
	private final Gson gson;
	private final String immichUrl;
	private final String apiKey;
	
	public ImmichEngine(HttpClient httpClient, Gson gson, String immichUrl, String API_KEY)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.immichUrl = URI.create(immichUrl).resolve("/api/").toString();
		this.apiKey = API_KEY;
	}
	
	public List<File> bulkUploadCheck(List<File> files)
			throws IOException, InterruptedException
	{
		var json = new JsonObject();
		
		Map<String, File> idMap = files.stream().collect(Collectors.toMap(f -> UUID.randomUUID().toString(), UnaryOperator.identity()));
		
		var assets = new JsonArray();
		
		idMap.entrySet()
			 .parallelStream()
			 .map(f ->
			 {
				 JsonObject o = new JsonObject();
				 o.addProperty("checksum", Hashers.SHA1.genHash(f.getValue()));
				 o.addProperty("id", f.getKey());
				 return o;
			 })
			 .forEach(assets::add);
		
		json.add("assets", assets);
		
		var request = authorized("assets/bulk-upload-check")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.toString()))
				.build();
		
		var response = gson.fromJson(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body(), JsonObject.class);
		var results = response.getAsJsonArray("results");
		
		return StreamSupport
				.stream(results.spliterator(), false)
				.map(JsonElement::getAsJsonObject)
				.filter(j -> j.getAsJsonPrimitive("action").getAsString().equalsIgnoreCase("accept"))
				.map(j -> j.getAsJsonPrimitive("id"))
				.map(JsonElement::getAsString)
				.map(idMap::get)
				.filter(Objects::nonNull)
				.toList();
	}
	
	public JsonElement addToAlbum(String album, List<JsonObject> objects)
			throws IOException, InterruptedException
	{
		var json = new JsonObject();
		
		var ids = new JsonArray();
		for(JsonObject object : objects)
		{
			if(object.has("id"))
			{
				ids.add(object.getAsJsonPrimitive("id"));
			}
		}
		
		json.add("ids", ids);
		
		var request = authorized("albums/" + album + "/assets")
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json.toString()))
				.build();
		
		var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		
		return gson.fromJson(response.body(), JsonElement.class);
	}
	
	public JsonObject uploadImage(File targetFile)
			throws IOException, InterruptedException
	{
		var times = Files.getFileAttributeView(targetFile.toPath(), BasicFileAttributeView.class);
		var attrs = times.readAttributes();
		
		UnaryOperator<Instant> nanoStripper = inst -> Instant.ofEpochMilli(inst.toEpochMilli());
		
		HTTPRequestMultipartBody multipartBody = new HTTPRequestMultipartBody.Builder()
				.addPart("deviceAssetId", "web-%s-%s".formatted(targetFile.getName(), Long.toUnsignedString(attrs.creationTime().toMillis())))
				.addPart("deviceId", "WEB")
				.addPart("fileCreatedAt", nanoStripper.apply(attrs.creationTime().toInstant()).toString())
				.addPart("fileModifiedAt", nanoStripper.apply(attrs.lastModifiedTime().toInstant()).toString())
				.addPart("isFavorite", "false")
				.addPart("duration", "0:00:00.000000")
				.addPart("assetData", targetFile, "application/octet-stream", targetFile.getName())
				.build();
		
		var request = authorized("assets")
				.header("Content-Type", multipartBody.getContentType())
				.POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody.getBody()))
				.build();
		
		var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		
		return gson.fromJson(response.body(), JsonObject.class);
	}
	
	private HttpRequest.Builder authorized(String sub)
	{
		return HttpRequest
				.newBuilder()
				.uri(URI.create(immichUrl + sub))
				.header("x-api-key", apiKey);
	}
}
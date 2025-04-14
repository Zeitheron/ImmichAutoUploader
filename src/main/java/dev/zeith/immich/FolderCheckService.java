package dev.zeith.immich;

import com.google.gson.JsonObject;
import dev.zeith.immich.cfg.FolderConfig;
import dev.zeith.immich.io.ImmichEngine;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

public class FolderCheckService
		implements Runnable
{
	protected final ImmichEngine immich;
	protected final FolderConfig config;
	
	protected final Pattern[] filters;
	
	protected final Map<Pattern, SimpleDateFormat> timeRemappers;
	
	protected final Duration period = Duration.ofSeconds(30L);
	
	public FolderCheckService(ImmichEngine immich, FolderConfig config)
	{
		this.immich = immich;
		this.config = config;
		this.filters = config.filePatterns().stream().map(Pattern::compile).toArray(Pattern[]::new);
		
		LinkedHashMap<Pattern, SimpleDateFormat> timeRemappers = new LinkedHashMap<>();
		for(Map.Entry<String, String> entry : config.timeRemappers().entrySet())
			timeRemappers.put(Pattern.compile(entry.getKey()), new SimpleDateFormat(
					entry.getValue()
						 .replace('.', '-')
						 .replace('_', '-')
						 .replace(' ', '-')
					)
			);
		this.timeRemappers = Collections.unmodifiableMap(timeRemappers);
	}
	
	protected boolean fileNameMatches(String name)
	{
		for(Pattern filter : filters)
			if(filter.matcher(name).find())
				return true;
		return false;
	}
	
	@SneakyThrows
	protected void runCheck(Path pth)
	{
		int md = config.maxDepth() <= 0 ? Integer.MAX_VALUE : config.maxDepth();
		
		var files = Files
				.walk(pth, md)
				.filter(Files::isRegularFile)
				.filter(p ->
				{
					var fn = p.getFileName().toString();
					
					int lastDot = fn.lastIndexOf('.');
					if(lastDot < 0) return false;
					
					return immich
							.getFlatMediaTypesCache()
							.contains(fn.substring(lastDot).toLowerCase(Locale.ROOT))
							&& fileNameMatches(fn);
				})
				.map(Path::toFile)
				// file wasn't touched for 5 seconds, probably safe to move it now
				.filter(f -> System.currentTimeMillis() - f.lastModified() > 5000L)
				.toList();
		
		var files2Upload = immich.bulkUploadCheck(files);
		
		if(!files2Upload.isEmpty())
		{
			System.out.println("Uploading " + files2Upload.size() + " images...");
			for(File file : files2Upload)
			{
				remapTimestamps(file);
				
				System.out.println("Uploading " + file.getName() + " . . .");
				JsonObject res = immich.uploadImage(file);
				if(res.has("status") && res.getAsJsonPrimitive("status").getAsString().equalsIgnoreCase("duplicate"))
				{
					System.out.println("File " + file.getName() + " already exists!");
					file.delete();
					continue;
				}
				
				if(res.has("id"))
				{
					System.out.println("Upload of " + file.getName() + " OK!");
					file.delete();
					
					String alb = config.albumId();
					if(alb != null && !alb.isBlank()) immich.addToAlbum(alb, List.of(res));
				}
			}
		}
		
		var skipFiles = new ArrayList<>(files);
		skipFiles.removeAll(files2Upload);
		for(File f : skipFiles) f.delete();
	}
	
	@Override
	public void run()
	{
		var pth = Path.of(config.path());
		System.out.println("Started folder watch service @ " + pth);
		
		while(true)
		{
			try
			{
				Thread.sleep(period);
			} catch(InterruptedException e)
			{
				break;
			}
			
			System.out.println("Tick Start @ " + pth);
			try
			{
				runCheck(pth);
			} catch(Exception e)
			{
				System.out.println("Failed to run tick!");
				e.printStackTrace(System.out);
			}
			System.out.println("Tick End @ " + pth);
		}
		
		System.out.println("Stopped folder watch service @ " + pth);
	}
	
	protected void remapTimestamps(File file)
	{
		var p = file.toPath();
		var name = p.toFile().getName();
		
		var match = timeRemappers
				.entrySet()
				.stream()
				.map(e -> Map.entry(e.getKey().matcher(name), e.getValue()))
				.filter(e -> e.getKey().find())
				.findFirst()
				.orElse(null);
		
		if(match == null)
			return;
		
		System.out.println("Remapping timestamps for " + file.getName());
		
		var time = match
				.getKey()
				.group("time")
				.replace('.', '-')
				.replace('_', '-')
				.replace(' ', '-');
		try
		{
			Date parse = match.getValue().parse(time);
			var inst = FileTime.from(parse.toInstant());
			BasicFileAttributeView attrs = Files.getFileAttributeView(p, BasicFileAttributeView.class);
			attrs.setTimes(inst, inst, inst);
			System.out.println("Timestamp for " + file.getName() + " has been remapped to " + inst.toInstant().toString());
		} catch(NumberFormatException nfe)
		{
			System.out.println("Failed to parse " + time + " inside " + match.getKey().group());
		} catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
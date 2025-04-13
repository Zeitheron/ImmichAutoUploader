package dev.zeith.immich;

import com.google.gson.*;
import dev.zeith.immich.cfg.*;
import dev.zeith.immich.io.ImmichEngine;
import lombok.SneakyThrows;

import java.io.File;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class Main
{
	private static final Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.serializeSpecialFloatingPointValues()
			.create();
	
	@SneakyThrows
	public static void main(String[] args)
	{
		var cfg = readConf();
		
		var engine = new ImmichEngine(HttpClient.newHttpClient(), gson, cfg.immichUrl(), cfg.apiKey());
		
		System.out.println("Connecting to " + cfg.immichUrl());
		engine.resolveMediaTypes();
		System.out.println("Supported " + engine.getFlatMediaTypesCache().size() + " file types.");
		
		var factory = Thread.ofVirtual().name("FolderServiceWatcher", 0L);
		
		CompletableFuture
				.allOf(cfg
						.folders()
						.stream()
						.map(fc -> CompletableFuture.runAsync(new FolderCheckService(engine, fc), factory::start))
						.toArray(CompletableFuture[]::new))
				.join();
		
		System.out.println("Folder watching threads have been halted. Exiting.");
	}
	
	@SneakyThrows
	private static MainConfig readConf()
	{
		var pth = new File("config.json").getAbsoluteFile().toPath();
		
		if(!Files.isRegularFile(pth))
		{
			MainConfig cfg = MainConfig.createDefault().revalidate();
			Files.writeString(pth, gson.toJson(cfg));
			return cfg;
		}
		
		var cfg = gson.fromJson(Files.readString(pth), MainConfig.class);
		var rev = cfg.revalidate();
		if(cfg != rev) Files.writeString(pth, gson.toJson(rev));
		return rev;
	}
}
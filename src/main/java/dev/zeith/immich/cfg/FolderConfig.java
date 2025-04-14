package dev.zeith.immich.cfg;

import lombok.With;

import java.io.File;
import java.util.*;

@With
public record FolderConfig(
		String path,
		String albumId,
		int maxDepth,
		List<String> filePatterns,
		Map<String, String> timeRemappers
)
{
	public FolderConfig revalidate()
	{
		var th = this;
		if(maxDepth < 0) th = th.withMaxDepth(0);
		if(path == null) th = th.withPath("");
		if(albumId == null) th = th.withAlbumId("");
		if(filePatterns == null || filePatterns.isEmpty()) th = th.withFilePatterns(List.of("^VRChat_.+\\.png$"));
		if(timeRemappers == null) th = th.withTimeRemappers(createVRChat().timeRemappers());
		return th;
	}
	
	public static FolderConfig createVRChat()
	{
		LinkedHashMap<String, String> timeRemappers = new LinkedHashMap<>();
		timeRemappers.put("(?<time>\\d{4}[-_.]\\d{2}[-_.]\\d{2}[-_.]\\d{2}[-_.]\\d{2}[-_.]\\d{2}[.]\\d{3})", "yyyy-MM-dd-HH-mm-ss-SSS");
		timeRemappers.put("(?<time>\\d{4}[-_.]\\d{2}[-_.]\\d{2}[-_.]\\d{2}[-_.]\\d{2}[-_.]\\d{2})", "yyyy-MM-dd-HH-mm-ss");
		
		return new FolderConfig(
				new File(System.getProperty("user.home"), "Pictures" + File.separator + "VRChat").getAbsolutePath(),
				null,
				0,
				List.of(
						("^VRChat_.+\\.png$")
				),
				Collections.unmodifiableMap(timeRemappers)
		);
	}
}
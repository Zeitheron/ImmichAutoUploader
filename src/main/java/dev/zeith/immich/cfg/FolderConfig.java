package dev.zeith.immich.cfg;

import lombok.With;

import java.io.File;
import java.util.List;

@With
public record FolderConfig(
		String path,
		String albumId,
		int maxDepth,
		List<String> filePatterns
)
{
	public FolderConfig revalidate()
	{
		var th = this;
		if(maxDepth < 0) th = th.withMaxDepth(0);
		if(path == null) th = th.withPath("");
		if(albumId == null) th = th.withAlbumId("");
		if(filePatterns == null || filePatterns.isEmpty()) th = th.withFilePatterns(List.of("^VRChat_.+\\.png$"));
		return th;
	}
	
	public static FolderConfig createVRChat()
	{
		return new FolderConfig(
				new File(System.getProperty("user.home"), "Pictures" + File.separator + "VRChat").getAbsolutePath(),
				null,
				0,
				List.of(
						("^VRChat_.+\\.png$")
				)
		);
	}
}
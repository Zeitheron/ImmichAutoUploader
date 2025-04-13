package dev.zeith.immich.cfg;

import lombok.With;

import java.util.*;

@With
public record MainConfig(
		String immichUrl,
		String apiKey,
		List<FolderConfig> folders
)
{
	public MainConfig revalidate()
	{
		var th = this;
		
		{
			var ch = false;
			List<FolderConfig> folders = new ArrayList<>(folders());
			for(int i = 0; i < folders.size(); i++)
			{
				var folder = folders.get(i);
				var f2 = folder.revalidate();
				if(folder != f2)
				{
					folders.set(i, f2);
					ch = true;
				}
			}
			if(ch) th = th.withFolders(List.copyOf(folders));
		}
		
		return th;
	}
	
	public static MainConfig createDefault()
	{
		return new MainConfig(
				"https://immich.yourdomain.com",
				Integer.toString(0).repeat(43),
				List.of(FolderConfig.createVRChat())
		);
	}
}
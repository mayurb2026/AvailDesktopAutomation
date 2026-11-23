package pages.thumbnails;

import java.util.ArrayList;
import java.util.List;

import base.BasePage;
import desktop.DesktopDriver;
import desktop.DesktopElement;
import desktop.DesktopLocator;
import pages.channels.ChannelPage;

/**
 * THUMBNAILS of the file tiles in the open channel (Channels tab -> one channel -> files list).
 *
 * PAGE OBJECT - locators in the constructor, actions as methods, NO assertions.
 *
 * LOCATORS
 *   CONFIRMED: files list AutomationId "ContentListView"; file tile = ListItem "Avail.UI.ViewModels.ContentViewModel";
 *              file name = text of the Button inside the tile.
 *   TO CONFIRM with Accessibility Insights on a channel WITH files: the thumbnail is the Image control inside the
 *              file tile (channel tiles use an Image with AutomationId "channelImage"; file tiles were not inspectable
 *              on 23-Nov-2026 because the open channel had no files). Update THUMBNAIL below if it differs.
 */
public class ThumbnailPage extends BasePage {

	/** Control type of the thumbnail inside a file tile (TO CONFIRM - see class comment). */
	private static final String THUMBNAIL = "Image";

	private final DesktopLocator fileTiles;

	public ThumbnailPage(DesktopDriver driver) {
		super(driver);
		fileTiles = window.byAutomationId("ContentListView").byControlType("ListItem").withName(ChannelPage.FILE_TILE);
	}

	/** One file tile: its file name and whether it shows a thumbnail image (with a size on screen). */
	public static final class Tile {
		public final String fileName;
		public final boolean hasThumbnail;
		public final int thumbnailWidth;
		public final int thumbnailHeight;

		Tile(String fileName, DesktopElement image) {
			this.fileName = fileName;
			this.hasThumbnail = image != null && image.width() > 0 && image.height() > 0;
			this.thumbnailWidth = image == null ? 0 : image.width();
			this.thumbnailHeight = image == null ? 0 : image.height();
		}

		@Override
		public String toString() {
			return fileName + (hasThumbnail ? " [thumbnail " + thumbnailWidth + "x" + thumbnailHeight + "]" : " [NO thumbnail]");
		}
	}

	/** Every file tile that exists right now, with its thumbnail information. */
	public List<Tile> getTiles() {
		List<Tile> tiles = new ArrayList<>();
		int n = fileTiles.count();
		for (int i = 0; i < n; i++) {
			DesktopLocator tile = fileTiles.nth(i);
			String name = tile.byControlType("Button").first().text();
			List<DesktopElement> images = tile.byControlType(THUMBNAIL).all();
			tiles.add(new Tile(name, images.isEmpty() ? null : images.get(0)));
		}
		return tiles;
	}

	/** Number of file tiles right now. */
	public int getTileCount() {
		return fileTiles.count();
	}
}

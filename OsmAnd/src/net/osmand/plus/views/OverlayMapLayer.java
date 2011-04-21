package net.osmand.plus.views;

import net.osmand.data.preparation.MapTileDownloader;
import net.osmand.data.preparation.MapTileDownloader.DownloadRequest;
import net.osmand.map.ITileSource;
import net.osmand.osm.MapUtils;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.ResourceManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.FloatMath;
import android.widget.SeekBar;

public class OverlayMapLayer implements OsmandMapLayer {
	
	private Paint paintBitmap;
	private OsmandMapTileView view;
	private ResourceManager mgr;
	
	// used only to save space & reuse
	protected RectF tilesRect = new RectF();
	protected RectF latlonRect = new RectF();
	protected Rect boundsRect = new Rect();
	protected RectF bitmapToDraw = new RectF();
	protected Rect bitmapToZoom = new Rect();

	private boolean visible = false;

	private SeekBar seekBar;
	private int alpha = 128;

	
	// name of source map
	public ITileSource map = null;
	
	public OverlayMapLayer(SeekBar seekBar){
		this.seekBar = seekBar;
	}
	
	@Override
	public boolean onLongPressEvent(PointF point) {
		return false;
	}
	
	
	@Override
	public boolean onTouchEvent(PointF point) {
		return false;
	}
	
	public ITileSource getMap() {
		return map;
	}
	
	public void setMap(ITileSource map) {
		this.map = map;
	}
	
	public void initLayer(OsmandMapTileView view) {
		this.view = view;
		mgr = view.getApplication().getResourceManager();

		paintBitmap = new Paint();
		paintBitmap.setAlpha(alpha);
		paintBitmap.setAntiAlias(true);
		paintBitmap.setFilterBitmap(true);	
	}
	
	private void calculateTileRectangle(Rect pixRect, float cx, float cy, float ctilex, float ctiley, RectF tileRect) {
		float x1 = view.calcDiffTileX(pixRect.left - cx, pixRect.top - cy);
		float x2 = view.calcDiffTileX(pixRect.left - cx, pixRect.bottom - cy);
		float x3 = view.calcDiffTileX(pixRect.right - cx, pixRect.top - cy);
		float x4 = view.calcDiffTileX(pixRect.right - cx, pixRect.bottom - cy);
		float y1 = view.calcDiffTileY(pixRect.left - cx, pixRect.top - cy);
		float y2 = view.calcDiffTileY(pixRect.left - cx, pixRect.bottom - cy);
		float y3 = view.calcDiffTileY(pixRect.right - cx, pixRect.top - cy);
		float y4 = view.calcDiffTileY(pixRect.right - cx, pixRect.bottom - cy);
		float l = Math.min(Math.min(x1, x2), Math.min(x3, x4)) + ctilex;
		float r = Math.max(Math.max(x1, x2), Math.max(x3, x4)) + ctilex;
		float t = Math.min(Math.min(y1, y2), Math.min(y3, y4)) + ctiley;
		float b = Math.max(Math.max(y1, y2), Math.max(y3, y4)) + ctiley;
		tileRect.set(l, t, r, b);
	}
	
	public void calculatePixelRectangle(Rect pixelRect, float cx, float cy, float ctilex, float ctiley, RectF tileRect) {
		float x1 = view.calcDiffPixelX(tileRect.left - ctilex, tileRect.top - ctiley);
		float x2 = view.calcDiffPixelX(tileRect.left - ctilex, tileRect.bottom - ctiley);
		float x3 = view.calcDiffPixelX(tileRect.right - ctilex, tileRect.top - ctiley);
		float x4 = view.calcDiffPixelX(tileRect.right - ctilex, tileRect.bottom - ctiley);
		float y1 = view.calcDiffPixelY(tileRect.left - ctilex, tileRect.top - ctiley);
		float y2 = view.calcDiffPixelY(tileRect.left - ctilex, tileRect.bottom - ctiley);
		float y3 = view.calcDiffPixelY(tileRect.right - ctilex, tileRect.top - ctiley);
		float y4 = view.calcDiffPixelY(tileRect.right - ctilex, tileRect.bottom - ctiley);
		int l = Math.round(Math.min(Math.min(x1, x2), Math.min(x3, x4)) + cx);
		int r = Math.round(Math.max(Math.max(x1, x2), Math.max(x3, x4)) + cx);
		int t = Math.round(Math.min(Math.min(y1, y2), Math.min(y3, y4)) + cy);
		int b = Math.round(Math.max(Math.max(y1, y2), Math.max(y3, y4)) + cy);	
		pixelRect.set(l, t, r, b);
	}
	
	@Override
	public void onDraw(Canvas canvas, RectF latLonBounds, boolean nightMode) {
		if (visible) {
			boolean useInternet = OsmandSettings.isUsingInternetToDownloadTiles(view.getSettings());
			if (useInternet) {
				MapTileDownloader.getInstance().refuseAllPreviousRequests();
			}
			float ftileSize = view.getTileSize();
			int tileSize = view.getSourceTileSize();
			
			int nzoom = view.getZoom();
			float tileX = (float) MapUtils.getTileNumberX(nzoom, view.getLongitude());
			float tileY = (float) MapUtils.getTileNumberY(nzoom, view.getLatitude());
			float w = view.getCenterPointX();
			float h = view.getCenterPointY();
			boundsRect.set(0, 0, view.getWidth(), view.getHeight());
			calculateTileRectangle(boundsRect, w, h, tileX, tileY, tilesRect);
			int left = (int) FloatMath.floor(tilesRect.left);
			int top = (int) FloatMath.floor(tilesRect.top );
			int width = (int) FloatMath.ceil(tilesRect.right - left);
			int height = (int) FloatMath.ceil(tilesRect.bottom - top);
			latlonRect.top = (float) MapUtils.getLatitudeFromTile(nzoom, tilesRect.top);
			latlonRect.left = (float) MapUtils.getLongitudeFromTile(nzoom, tilesRect.left);
			latlonRect.bottom = (float) MapUtils.getLatitudeFromTile(nzoom, tilesRect.bottom);
			latlonRect.right = (float) MapUtils.getLongitudeFromTile(nzoom, tilesRect.right);
			
			if (map != null) {
				//log.debug("map.getNamet="+map.getName());
				//log.debug("map.geturlToLoad="+map.getUrlToLoad(12, 12, 12));
				//log.debug("map.couldBeDownloadedFromInternet="+map.couldBeDownloadedFromInternet());
				
				useInternet = useInternet && OsmandSettings.isInternetConnectionAvailable(view.getContext())
							&& map.couldBeDownloadedFromInternet();
				int maxLevel = Math.min(OsmandSettings.getMaximumLevelToDownloadTile(view.getSettings()), map.getMaximumZoomSupported());
				
				for (int i = 0; i < width; i++) {
					for (int j = 0; j < height; j++) {
						float x1 = (i + left - tileX) * ftileSize + w;
						float y1 = (j + top - tileY) * ftileSize + h;
						String ordImgTile = mgr.calculateTileId(map, left + i, top + j, nzoom);
						// asking tile image async
						boolean imgExist = mgr.tileExistOnFileSystem(ordImgTile, map, left + i, top + j, nzoom);
						Bitmap bmp = null;
						boolean originalBeLoaded = useInternet && nzoom <= maxLevel;
						if (imgExist || originalBeLoaded) {
							bmp = mgr.getTileImageForMapAsync(ordImgTile, map, left + i, top + j, nzoom, useInternet);
						}
						if (bmp == null) {
							int div = 2;
							// asking if there is small version of the map (in cache)
							String imgTile2 = mgr.calculateTileId(map, (left + i) / 2, (top + j) / 2, nzoom - 1);
							String imgTile4 = mgr.calculateTileId(map, (left + i) / 4, (top + j) / 4, nzoom - 2);
							if (originalBeLoaded || imgExist) {
								bmp = mgr.getTileImageFromCache(imgTile2);
								div = 2;
								if (bmp == null) {
									bmp = mgr.getTileImageFromCache(imgTile4);
									div = 4;
								}
							}
							if (!originalBeLoaded && !imgExist) {
								if (mgr.tileExistOnFileSystem(imgTile2, map, (left + i) / 2, (top + j) / 2, nzoom - 1)
										|| (useInternet && nzoom - 1 <= maxLevel)) {
									bmp = mgr.getTileImageForMapAsync(imgTile2, map, (left + i) / 2, (top + j) / 2, nzoom - 1, useInternet);
									div = 2;
								} else if (mgr.tileExistOnFileSystem(imgTile4, map, (left + i) / 4, (top + j) / 4, nzoom - 2)
										|| (useInternet && nzoom - 2 <= maxLevel)) {
									bmp = mgr.getTileImageForMapAsync(imgTile4, map, (left + i) / 4, (top + j) / 4, nzoom - 2, useInternet);
									div = 4;
								}
							}
							
							if (bmp != null) {
								int xZoom = ((left + i) % div) * tileSize / div;
								int yZoom = ((top + j) % div) * tileSize / div;
								bitmapToZoom.set(xZoom, yZoom, xZoom + tileSize / div, yZoom + tileSize / div);
								bitmapToDraw.set(x1, y1, x1 + ftileSize, y1 + ftileSize);
								canvas.drawBitmap(bmp, bitmapToZoom, bitmapToDraw, paintBitmap);
							}
						} else {
							bitmapToZoom.set(0, 0, map.getTileSize(), map.getTileSize());
							bitmapToDraw.set(x1, y1, x1 + ftileSize, y1 + ftileSize);
							canvas.drawBitmap(bmp, bitmapToZoom, bitmapToDraw, paintBitmap);
						}
					}
				}
			}
		}
	}
	
	public void tileDownloaded(Canvas canvas, DownloadRequest request) {
		float w = view.getCenterPointX();
		float h = view.getCenterPointY();
		int nzoom = view.getZoom();
		float tileX = (float) MapUtils.getTileNumberX(nzoom, view.getLongitude());
		float tileY = (float) MapUtils.getTileNumberY(nzoom, view.getLatitude());
		
		Bitmap bmp = null;
		if (map != null) {
			ResourceManager mgr = view.getApplication().getResourceManager();
			bmp = mgr.getTileImageForMapSync(null, map, request.xTile, request.yTile, request.zoom, false);
		}
		
		float tileSize = view.getTileSize();
		float x = (request.xTile - tileX) * tileSize + w;
		float y = (request.yTile - tileY) * tileSize + h;
		if (bmp != null) {
			bitmapToZoom.set(0, 0, view.getSourceTileSize(), view.getSourceTileSize());
			bitmapToDraw.set(x, y, x + tileSize, y + tileSize);
			canvas.drawBitmap(bmp, bitmapToZoom, bitmapToDraw, paintBitmap);
		}
	}
	
	public void setVisible(boolean visible) {
		this.visible = visible;
	}
	
	public boolean isVisible() {
		return visible;
	}
	
	public void setAlpha(int alpha) {
		this.alpha = alpha;
		paintBitmap.setAlpha(alpha);
	}
	
	public int getAlpha() {
		return this.alpha;
	}

	@Override
	public void destroyLayer() {
		
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

}

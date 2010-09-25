package net.osmand.render;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.osmand.IProgress;
import net.osmand.LogUtil;
import net.osmand.data.index.IndexConstants;
import net.osmand.osm.MapRenderObject;
import net.osmand.osm.MapRenderingTypes;
import net.osmand.osm.MapUtils;
import net.osmand.osm.MultyPolygon;

import org.apache.commons.logging.Log;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.FloatMath;

public class MapRenderRepositories {
	
	private final static Log log = LogUtil.getLog(MapRenderRepositories.class);
	private final Context context;
	private Map<String, Connection> files = new LinkedHashMap<String, Connection>();
	private Map<Connection, RectF> connections = new LinkedHashMap<Connection, RectF>();
	private Map<Connection, PreparedStatement> pZoom0 = new LinkedHashMap<Connection, PreparedStatement>();
	private Map<Connection, PreparedStatement> pZoom1 = new LinkedHashMap<Connection, PreparedStatement>();
	private Map<Connection, PreparedStatement> pZoom2 = new LinkedHashMap<Connection, PreparedStatement>();
	private OsmandRenderer renderer;
	
	private double cTopY;
	private double cBottomY;
	private double cLeftX;
	private double cRightX;
	private int cZoom;
	private float cRotate;
	
	// cached objects in order to rotate without 
	private List<MapRenderObject> cObjects = new LinkedList<MapRenderObject>();
	private RectF cachedWaysLoc = new RectF();
	private float cachedRotate = 0;
	private Bitmap bmp;
	
	
	
	public MapRenderRepositories(Context context){
		this.context = context;
		this.renderer = new OsmandRenderer(context);
	}
	
	public Context getContext() {
		return context;
	}
	
	private RectF getBoundsForIndex(Statement stat, String tableName) throws SQLException{
		ResultSet rs = stat.executeQuery("SELECT MIN(minLon), MAX(maxLat), MAX(maxLon), MIN(minLat) FROM " + IndexConstants.indexMapLocationsTable); //$NON-NLS-1$
		RectF bounds = new RectF();
		if(rs.next()){
			bounds.set(rs.getFloat(1), rs.getFloat(2), rs.getFloat(3), rs.getFloat(4));
		}
		rs.close();
		return bounds;
	}
	
	
	public boolean initializeNewResource(final IProgress progress, File file) {
		long start = System.currentTimeMillis();
		Connection conn = null;
		if(files.containsKey(file.getAbsolutePath())){
			closeConnection(files.get(file.getAbsolutePath()), file.getAbsolutePath());
		}
		try {
			try {
				Class.forName("org.sqlite.JDBC"); //$NON-NLS-1$
			} catch (Exception e) {
				log.error("Could not load driver", e); //$NON-NLS-1$
				return false;
			}
			conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath()); //$NON-NLS-1$
			PreparedStatement pStatement = conn.prepareStatement(loadMapQuery);
			PreparedStatement pStatement2 = conn.prepareStatement(loadMapQuery2);
			PreparedStatement pStatement3 = conn.prepareStatement(loadMapQuery3);
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("PRAGMA user_version"); //$NON-NLS-1$
			int v = rs.getInt(1);
			rs.close();
			if(v != IndexConstants.MAP_TABLE_VERSION){
				return false;
			}
			RectF bounds = foundBounds(stat);
			
			
			stat.close();
			connections.put(conn, bounds);
			files.put(file.getAbsolutePath(), conn);
			pZoom0.put(conn, pStatement);
			pZoom1.put(conn, pStatement2);
			pZoom2.put(conn, pStatement3);
			
		} catch (Exception e) {
			log.error("No connection", e); //$NON-NLS-1$
			if(conn != null){
				try {
					conn.close();
				} catch (java.sql.SQLException e1) {
				}
				conn = null;
			}
			return false;
		}
		if (log.isDebugEnabled()) {
			log.debug("Initializing db " + file.getAbsolutePath() + " " + (System.currentTimeMillis() - start) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return true;
	}

	private RectF foundBounds(Statement stat) throws SQLException {
		String metaTable = "loc_meta_locations"; //$NON-NLS-1$
		ResultSet rs = stat.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='"+metaTable+"'");  //$NON-NLS-1$//$NON-NLS-2$
		boolean dbExist = rs.next();
		rs.close();
		boolean found = false;
		boolean write = true;
		RectF bounds = new RectF();
		if(dbExist){
			rs = stat.executeQuery("SELECT MAX_LAT, MIN_LON, MIN_LAT, MAX_LON  FROM " +metaTable); //$NON-NLS-1$
			if(rs.next()){
				bounds.set(rs.getFloat(2), rs.getFloat(1), rs.getFloat(4), rs.getFloat(3));
				found = true;
			} else {
				found = false;
			}
			rs.close();
		} else {
			try {
				stat.execute("CREATE TABLE " + metaTable + " (MAX_LAT DOUBLE, MIN_LON DOUBLE, MIN_LAT DOUBLE, MAX_LON DOUBLE)"); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (RuntimeException e) {
				// case when database is in readonly mode
				write = false;
			}
		}
		
		if (!found) {
			bounds = getBoundsForIndex(stat, IndexConstants.indexMapLocationsTable);
			if(bounds.left == bounds.right || bounds.bottom == bounds.top){
				bounds = getBoundsForIndex(stat, IndexConstants.indexMapLocationsTable2);
				if(bounds.left == bounds.right || bounds.bottom == bounds.top){
					bounds = getBoundsForIndex(stat, IndexConstants.indexMapLocationsTable3);
				}
			}
			if (write) {
				stat.execute("INSERT INTO " + metaTable + " VALUES ("+Double.toString(bounds.top)+ ", "+Double.toString(bounds.left)+ ", " +  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						Double.toString(bounds.bottom)+ ", "+Double.toString(bounds.right)+ ")");  //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		
		return bounds;
	}
	
	
	// if cache was changed different instance will be returned
	public RectF getCachedWaysLoc() {
		return cachedWaysLoc;
	}
	
	public float getCachedRotate() {
		return cachedRotate;
	}
	
	protected void closeConnection(Connection c, String file){
		files.remove(c);
		connections.remove(c);
		pZoom0.remove(c);
		pZoom1.remove(c);
		pZoom2.remove(c);
		try {
			c.close();
		} catch (java.sql.SQLException e) {
		}
	}
	
	public void clearAllResources(){
		clearCache();
		for(String f : files.keySet()){
			closeConnection(files.get(f), f);
		}
	}
	
	
	public boolean updateMapIsNeeded(RectF tileRect, int zoom, float rotate){
		if (connections.isEmpty()) {
			return false;
		}
		boolean inside = insideBox(tileRect.top, tileRect.left, tileRect.bottom,  tileRect.right, zoom);
		if(rotate < 0){
			rotate += 360;
		} 

		return !inside || Math.abs(rotate - cRotate) > 15; // leave only 15 to find that UI box out of searched 
		
	}

	public boolean isEmpty(){
		return connections.isEmpty();
	}

//	MapUtils.getLatitudeFromTile(17, topY)
	private boolean insideBox(double topY, double leftX, double bottomY, double rightX, int zoom) {
		boolean inside = cZoom == zoom && cTopY <= topY && cLeftX <= leftX && cRightX >= rightX
				&& cBottomY >= bottomY;
		return inside;
	}


	private static String loadMapQuery = "SELECT "+IndexConstants.IndexMapRenderObject.ID +", " + IndexConstants.IndexMapRenderObject.NODES +", " +  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
										IndexConstants.IndexMapRenderObject.NAME + ", " + IndexConstants.IndexMapRenderObject.TYPE + //$NON-NLS-1$
										" FROM " + IndexConstants.IndexMapRenderObject.getTable() +	" WHERE "+IndexConstants.IndexMapRenderObject.ID+  //$NON-NLS-1$//$NON-NLS-2$
											" IN (SELECT id FROM "+IndexConstants.indexMapLocationsTable +   //$NON-NLS-1$
												" WHERE ? <  maxLat AND ? > minLat AND maxLon > ? AND minLon  < ?)"; //$NON-NLS-1$
	
	private static String loadMapQuery2 = "SELECT "+IndexConstants.IndexMapRenderObject.ID +", " + IndexConstants.IndexMapRenderObject.NODES +", " +  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
										  IndexConstants.IndexMapRenderObject.NAME + ", " + IndexConstants.IndexMapRenderObject.TYPE + //$NON-NLS-1$
										  " FROM " + IndexConstants.IndexMapRenderObject.getTable() +	" WHERE "+IndexConstants.IndexMapRenderObject.ID+  //$NON-NLS-1$//$NON-NLS-2$
										  " IN (SELECT id FROM "+IndexConstants.indexMapLocationsTable2 +   //$NON-NLS-1$
										  " WHERE ? <  maxLat AND ? > minLat AND maxLon > ? AND minLon  < ?)"; //$NON-NLS-1$
	
	
	private static String loadMapQuery3 = "SELECT "+IndexConstants.IndexMapRenderObject.ID +", " + IndexConstants.IndexMapRenderObject.NODES +", " +  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
										  IndexConstants.IndexMapRenderObject.NAME + ", " + IndexConstants.IndexMapRenderObject.TYPE + //$NON-NLS-1$
										  " FROM " + IndexConstants.IndexMapRenderObject.getTable() +	" WHERE "+IndexConstants.IndexMapRenderObject.ID +  //$NON-NLS-1$//$NON-NLS-2$
										  " IN (SELECT id FROM "+IndexConstants.indexMapLocationsTable3 +   //$NON-NLS-1$
										  " WHERE ? <  maxLat AND ? > minLat AND maxLon > ? AND minLon  < ?)"; //$NON-NLS-1$
	
	public synchronized void loadMap(RectF tileRect, RectF boundsTileRect, int zoom, float rotate) {
		// currently doesn't work properly (every rotate bounds will be outside)
		boolean inside = insideBox(boundsTileRect.top, boundsTileRect.left, boundsTileRect.bottom, boundsTileRect.right, zoom);
		cRotate = rotate < 0 ? rotate + 360 : rotate;
		if (!inside) {
			cTopY = boundsTileRect.top;
			cLeftX = boundsTileRect.left;
			cRightX = boundsTileRect.right;
			cBottomY = boundsTileRect.bottom;
			double cBottomLatitude = MapUtils.getLatitudeFromTile(zoom, cBottomY);
			double cTopLatitude = MapUtils.getLatitudeFromTile(zoom, cTopY);
			double cLeftLongitude = MapUtils.getLongitudeFromTile(zoom, cLeftX);
			double cRightLongitude = MapUtils.getLongitudeFromTile(zoom, cRightX);
			cZoom = zoom;

			log.info(String.format("BLat=%s, TLat=%s, LLong=%s, RLong=%s, zoom=%s", //$NON-NLS-1$
					cBottomLatitude, cTopLatitude, cLeftLongitude, cRightLongitude, cZoom)); 

			long now = System.currentTimeMillis();


				
			if (connections.isEmpty()) {
				cObjects = new ArrayList<MapRenderObject>();
				// keep old results
				return;
			}
			try {
				int count = 0;
				cObjects = new ArrayList<MapRenderObject>();
				System.gc(); // to clear previous objects
				Set<Long> ids = new LinkedHashSet<Long>();
				Map<Integer, List<MapRenderObject>> multiPolygons = new LinkedHashMap<Integer, List<MapRenderObject>>();
				for (Connection c : connections.keySet()) {
					RectF r = connections.get(c);
					boolean intersects = r.top >= cBottomLatitude  && r.left <= cRightLongitude && r.right >= cLeftLongitude &&
											r.bottom <= cTopLatitude;
					if(!intersects){
						continue;
					}
					
					PreparedStatement statement = null;
					if (zoom >= 15) {
						statement = pZoom0.get(c);
					} else if (zoom >= 10) {
						statement = pZoom1.get(c);
					} else if (zoom >= 6) {
						statement = pZoom2.get(c);
					}
					statement.setDouble(1, cBottomLatitude);
					statement.setDouble(2, cTopLatitude);
					statement.setDouble(3, cLeftLongitude);
					statement.setDouble(4, cRightLongitude);
					ResultSet result = statement.executeQuery();


					try {
						while (result.next()) {
							long id = result.getLong(1);
							if (PerformanceFlags.checkForDuplicateObjectIds) {
								if (ids.contains(id)) {
									// do not add object twice
									continue;
								}
								ids.add(id);
							}
							int type = result.getInt(4);
							MapRenderObject obj = new MapRenderObject(id);
							obj.setType(type);
							obj.setData(result.getBytes(2));
							obj.setName(result.getString(3));
							
							count++;
							int mainType = obj.getMainType();
							// be attentive we need 16 bits from main type (not 15 bits!) 
							// the last bit shows direction of multipolygon way 
							registerMultipolygon(multiPolygons, mainType, obj);
							int sec = obj.getSecondType();
							if(sec != 0){
								registerMultipolygon(multiPolygons, sec, obj);
							}
							for (int k = 0; k < obj.getMultiTypes(); k++) {
								registerMultipolygon(multiPolygons, obj.getAdditionalType(k), obj);
							}
							cObjects.add(obj);
						}

					} finally {
						result.close();
					}
				}
				int leftX = MapUtils.get31TileNumberX(cLeftLongitude);
				int rightX = MapUtils.get31TileNumberX(cRightLongitude);
				int bottomY = MapUtils.get31TileNumberY(cBottomLatitude);
				int topY = MapUtils.get31TileNumberY(cTopLatitude);
				List<MultyPolygon> pMulti = proccessMultiPolygons(multiPolygons, leftX, rightX, bottomY, topY);
				cObjects.addAll(pMulti);
				log.info(String
						.format("Search has been done in %s ms. %s results were found.", System.currentTimeMillis() - now, count)); //$NON-NLS-1$
			} catch (java.sql.SQLException e) {
				log.debug("Search failed", e); //$NON-NLS-1$
			}
		}
		
		// create new instance to distinguish that cache was changed
		RectF newLoc = new RectF((float)MapUtils.getLongitudeFromTile(zoom, tileRect.left), (float)MapUtils.getLatitudeFromTile(zoom, tileRect.top),
				(float)MapUtils.getLongitudeFromTile(zoom, tileRect.right), (float)MapUtils.getLatitudeFromTile(zoom, tileRect.bottom));
		
		int width = (int) calcDiffPixelX(cRotate, tileRect.right - tileRect.left, tileRect.bottom - tileRect.top);
		int height = (int) calcDiffPixelY(cRotate, tileRect.right - tileRect.left, tileRect.bottom - tileRect.top);
		Bitmap bmp = renderer.generateNewBitmap(width, height, tileRect.left, tileRect.top, cObjects, cZoom, cRotate);
		Bitmap oldBmp = this.bmp;
		this.bmp = bmp;
		cachedWaysLoc = newLoc;
		cachedRotate = cRotate;
		if(oldBmp != null){
			oldBmp.recycle();
		}
		
	}
	
	public Bitmap getBitmap() {
		return bmp;
	}
	
	public float calcDiffPixelX(float rotate, float dTileX, float dTileY){
		float rad = (float) Math.toRadians(rotate);
		return (FloatMath.cos(rad) * dTileX - FloatMath.sin(rad) * dTileY) * OsmandRenderer.TILE_SIZE;
	}
	
	public float calcDiffPixelY(float rotate, float dTileX, float dTileY){
		float rad = (float) Math.toRadians(rotate);
		return (FloatMath.sin(rad) * dTileX + FloatMath.cos(rad) * dTileY) * OsmandRenderer.TILE_SIZE;
	}
	
	public synchronized void clearCache() {
		cObjects.clear();
		cBottomY = cLeftX = cRightX = cTopY = cRotate = cZoom = 0;
		if(bmp != null){
			bmp.recycle();
			bmp = null;
		}
		cachedWaysLoc = new RectF();
	}

	
	/// Manipulating with multipolygons
	private void registerMultipolygon(Map<Integer, List<MapRenderObject>> multyPolygons, int type, MapRenderObject obj) {
		if ((type & 0x3) == MapRenderingTypes.MULTY_POLYGON_TYPE) {
			type &= 0xffff; // reject attrs
			// multy polygon
			if (type != 0) {
				if (!multyPolygons.containsKey(type)) {
					multyPolygons.put(type, new ArrayList<MapRenderObject>());
				}
				multyPolygons.get(type).add(obj);
			}

		}
	}
	
	public List<MultyPolygon> proccessMultiPolygons(Map<Integer, List<MapRenderObject>> multyPolygons, int leftX, int rightX, int bottomY, int topY){
		List<MultyPolygon> listPolygons = new ArrayList<MultyPolygon>(multyPolygons.size());
		List<List<Long>> completedRings = new ArrayList<List<Long>>();
		List<List<Long>> incompletedRings = new ArrayList<List<Long>>();
		for (Integer type : multyPolygons.keySet()) {
			// TODO check another situation that there is only inverseList!!!
			if(((type >> 15) & 1) == 1){
				// continue on inner boundaries
				continue;
			} 
			int inverseType = (type | (1<< 15));
			List<MapRenderObject> directList = multyPolygons.get(type);
			List<MapRenderObject> inverselist = Collections.emptyList();
			if(multyPolygons.containsKey(inverseType)){
				inverselist = multyPolygons.get(inverseType);
			}
			// TODO remove log
//			System.out.println("Multipolygon t="+MapRenderingTypes.getMainObjectType(type)+
//					" subtype=" + MapRenderingTypes.getObjectSubType(type));
			
			completedRings.clear();
			incompletedRings.clear();
			
			MultyPolygon pl = processMultiPolygon(leftX, rightX, bottomY, topY, listPolygons, completedRings, incompletedRings, type,
					directList, inverselist);
			listPolygons.add(pl);
		}
		return listPolygons;
	}

	private MultyPolygon processMultiPolygon(int leftX, int rightX, int bottomY, int topY, List<MultyPolygon> listPolygons,
			List<List<Long>> completedRings, List<List<Long>> incompletedRings, Integer type, List<MapRenderObject> directList,
			List<MapRenderObject> inverselist) {
		MultyPolygon pl = new MultyPolygon();
		pl.setType(type << 1);
		for (int km = 0; km < 2; km++) {
			List<MapRenderObject> list = km == 0 ? directList : inverselist;
			for (MapRenderObject o : list) {
				int len = o.getPointsLength();
				if (len < 2) {
					continue;
				}
				List<Long> coordinates = new ArrayList<Long>(len / 2);
				if (o.getName() != null) {
					pl.setName(o.getName());
				}
				int px = o.getPoint31XTile(km == 0 ? 0 : len - 1);
				int py = o.getPoint31YTile(km == 0 ? 0 : len - 1);
				int x = px;
				int y = py;
				boolean pinside = leftX <= x && x <= rightX && y >= topY && y <= bottomY;
				if (pinside) {
					coordinates.add((((long) x) << 32) | ((long) y));
				}
				for (int i = 1; i < len; i++) {
					x = o.getPoint31XTile(km == 0 ? i : len - i - 1);
					y = o.getPoint31YTile(km == 0 ? i : len - i - 1);
					boolean inside = leftX <= x && x <= rightX && y >= topY && y <= bottomY;
					calculateLineCoordinates(inside, x, y, pinside, px, py, leftX, rightX, bottomY, topY, coordinates);
					px = x;
					py = y;
					pinside = inside;
				}
				if (coordinates.size() > 0) {
					if (coordinates.get(0).longValue() == coordinates.get(coordinates.size() - 1).longValue()) {
						completedRings.add(coordinates);
					} else {
						boolean add = true;
						for (int k = 0; k < incompletedRings.size();) {
							boolean remove = false;
							List<Long> i = incompletedRings.get(k);
							if (coordinates.get(0).longValue() == i.get(i.size() - 1).longValue()) {
								i.addAll(coordinates.subList(1, coordinates.size()));
								remove = true;
								coordinates = i;
							} else if (coordinates.get(coordinates.size() - 1).longValue() == i.get(0).longValue()) {
								coordinates.addAll(i.subList(1, i.size()));
								remove = true;
							}
							if (remove) {
								incompletedRings.remove(k);
							} else {
								k++;
							}
							if (coordinates.get(0).longValue() == coordinates.get(coordinates.size() - 1).longValue()) {
								completedRings.add(coordinates);
								add = false;
								break;
							}
						}
						if (add) {
							incompletedRings.add(coordinates);
						}
					}
				}
			}
		}
		// TODO check if there is only inner bounds (add path bound)
		if (incompletedRings.size() > 0) {
			unifyIncompletedRings(incompletedRings, completedRings, leftX, rightX, bottomY, topY);
		}
		
		long[][] lns = new long[completedRings.size()][];
		for (int i = 0; i < completedRings.size(); i++) {
			List<Long> ring = completedRings.get(i);
			lns[i] = new long[ring.size()];
			for (int j = 0; j < lns[i].length; j++) {
				lns[i][j] = ring.get(j);
			}
		}
		pl.setLines(lns);
		return pl;
	}

	private void unifyIncompletedRings(List<List<Long>> incompletedRings, List<List<Long>> completedRings, int leftX, int rightX,
			int bottomY, int topY) {
		int mask = 0xffffffff;
		for (List<Long> i : incompletedRings) {
			int x = (int) (i.get(i.size() - 1) >> 32);
			int y = (int) (i.get(i.size() - 1) & mask);
			int sx = (int) (i.get(0) >> 32);
			int sy = (int) (i.get(0) & mask);
			long start = (((long) sx) << 32) | ((long) sy);
			boolean st = false;
			for (int h = 0; h < 8; h++) {
				if (h % 4 == 0) {
					// top
					if (!st) {
						if (y == topY) {
							if (sy == topY && sx >= x) {
								i.add(start);
								break;
							} else {
								i.add((((long) rightX) << 32) | ((long) topY));
								st = true;
							}
						}
					} else if (sy == topY) {
						i.add(start);
						break;
					}
				} else if (h % 4 == 1) {
					// right
					if (!st) {
						if (x == rightX) {
							if (sx == rightX && sy >= y) {
								i.add(start);
								break;
							} else {
								i.add((((long) rightX) << 32) | ((long) bottomY));
								st = true;
							}
						}
					} else if (sx == rightX) {
						i.add(start);
						break;
					}
				} else if (h % 4 == 2) {
					// bottom
					if (!st) {
						if (y == bottomY) {
							if (sy == bottomY && sx <= x) {
								i.add(start);
								break;
							} else {
								i.add((((long) leftX) << 32) | ((long) bottomY));
								st = true;
							}
						}
					} else if (sy == bottomY) {
						i.add(start);
						break;
					}
				} else if (h % 4 == 3) {
					// left
					if (!st) {
						if (x == leftX) {
							if (sx == leftX && sy <= y) {
								i.add(start);
								break;
							} else {
								i.add((((long) leftX) << 32) | ((long) topY));
								st = true;
							}
						}
					} else if (sx == leftX) {
						i.add(start);
						break;
					}
				}
				// something wrong here
				// These excecptions could be commented and do not add line to completed
				if (h == 3 && !st) {
					throw new IllegalArgumentException();
				}
				if (h == 7) {
					throw new IllegalArgumentException();
				}
			}
			completedRings.add(i);
		}
		
		
	}

	private void calculateLineCoordinates(boolean inside, int x, int y, boolean pinside, int px, int py, int leftX, int rightX,
			int bottomY, int topY, List<Long> coordinates) {
		if (pinside) {
			if (inside) {
				coordinates.add((((long) x) << 32) | ((long) y));
			} else {
				int by = -1;
				int bx = -1;
				if (by == -1 && y < topY && py >= topY) {
					int tx = (int) (px + ((double) (x - px) * (topY - py)) / (y - py));
					if (leftX <= tx && tx <= rightX) {
						bx = tx;
						by = topY;
					}
				}
				if (by == -1 && y > bottomY && py <= bottomY) {
					int tx = (int) (px + ((double) (x - px) * (py - bottomY)) / (py - y));
					if (leftX <= tx && tx <= rightX) {
						bx = tx;
						by = bottomY;
					}
				}
				if (by == -1 && x < leftX && px >= leftX) {
					by = (int) (py + ((double) (y - py) * (leftX - px)) / (x - px));
					bx = leftX;
				}
				if (by == -1 && x > rightX && px <= rightX) {
					by = (int) (py + ((double) (y - py) * (px - rightX)) / (px - x));
					bx = rightX;
				}
				coordinates.add((((long) bx) << 32) | ((long) by));
			}
		} else if (inside) {
			int by = -1;
			int bx = -1;
			if (by == -1 && py < topY && y >= topY) {
				int tx = (int) (px + ((double) (x - px) * (topY - py)) / (y - py));
				if (leftX <= tx && tx <= rightX) {
					bx = tx;
					by = topY;
				}
			}
			if (by == -1 && py > bottomY && y <= bottomY) {
				int tx = (int) (px + ((double) (x - px) * (py - bottomY)) / (py - y));
				if (leftX <= tx && tx <= rightX) {
					bx = tx;
					by = bottomY;
				}
			}
			if (by == -1 && px < leftX && x >= leftX) {
				by = (int) (py + ((double) (y - py) * (leftX - px)) / (x - px));
				bx = leftX;
			}
			if (by == -1 && px > rightX && x <= rightX) {
				by = (int) (py + ((double) (y - py) * (px - rightX)) / (px - x));
				bx = rightX;
			}
			coordinates.add((((long) bx) << 32) | ((long) by));
		}
	}


}
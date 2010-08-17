package net.osmand.data.index;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import net.osmand.LogUtil;
import net.osmand.data.Building;
import net.osmand.data.City;
import net.osmand.data.Street;
import net.osmand.data.City.CityType;
import net.osmand.data.index.IndexConstants.IndexBuildingTable;
import net.osmand.data.index.IndexConstants.IndexCityTable;
import net.osmand.data.index.IndexConstants.IndexStreetTable;

import org.apache.commons.logging.Log;


public class DataIndexReader {
	private static final Log log = LogUtil.getLog(DataIndexReader.class);
	
	public Connection getConnection(File file) throws SQLException{
		try {
			Class.forName("org.sqlite.JDBC"); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			log.error("Illegal configuration", e); //$NON-NLS-1$
			throw new IllegalStateException(e);
		}
        return DriverManager.getConnection("jdbc:sqlite:"+file.getAbsolutePath()); //$NON-NLS-1$
	}
	
	
	public List<City> readCities(Connection c) throws SQLException{
		List<City> cities = new ArrayList<City>();
		Statement stat = c.createStatement();
		ResultSet set = stat.executeQuery(IndexConstants.generateSelectSQL(IndexCityTable.values()));
		while(set.next()){
			City city = new City(CityType.valueFromString(set.getString(IndexCityTable.CITY_TYPE.ordinal() + 1)));
			city.setName(set.getString(IndexCityTable.NAME.ordinal() + 1));
			city.setEnName(set.getString(IndexCityTable.NAME_EN.ordinal() + 1));
			city.setLocation(set.getDouble(IndexCityTable.LATITUDE.ordinal() + 1), 
					set.getDouble(IndexCityTable.LONGITUDE.ordinal() + 1));
			city.setId(set.getLong(IndexCityTable.ID.ordinal() + 1));
			cities.add(city);
			
		}
		set.close();
		stat.close();
		return cities;
	}
	
	public List<Street> readStreets(Connection c, City city) throws SQLException{
		List<Street> streets = new ArrayList<Street>();
		Statement stat = c.createStatement();
		ResultSet set = stat.executeQuery(IndexConstants.generateSelectSQL(IndexStreetTable.values(), 
				IndexStreetTable.CITY.toString() +" = " + city.getId())); //$NON-NLS-1$
		while(set.next()){
			Street street = new Street(city);
			street.setName(set.getString(IndexStreetTable.NAME.ordinal() + 1));
			street.setEnName(set.getString(IndexStreetTable.NAME_EN.ordinal() + 1));
			street.setLocation(set.getDouble(IndexStreetTable.LATITUDE.ordinal() + 1), 
					set.getDouble(IndexStreetTable.LONGITUDE.ordinal() + 1));
			street.setId(set.getLong(IndexStreetTable.ID.ordinal() + 1));
			streets.add(street);
		}
		set.close();
		stat.close();
		return streets;
	}
	
	public List<Building> readBuildings(Connection c, Street street) throws SQLException{
		List<Building> buildings = new ArrayList<Building>();
		Statement stat = c.createStatement();
		ResultSet set = stat.executeQuery(IndexConstants.generateSelectSQL(IndexBuildingTable.values(), 
				IndexBuildingTable.STREET.toString() +" = " + street.getId())); //$NON-NLS-1$
		while(set.next()){
			Building building = new Building();
			building.setName(set.getString(IndexBuildingTable.NAME.ordinal() + 1));
			building.setEnName(set.getString(IndexBuildingTable.NAME_EN.ordinal() + 1));
			building.setLocation(set.getDouble(IndexBuildingTable.LATITUDE.ordinal() + 1), 
					set.getDouble(IndexBuildingTable.LONGITUDE.ordinal() + 1));
			building.setId(set.getLong(IndexBuildingTable.ID.ordinal() + 1));
			buildings.add(building);
		}
		set.close();
		stat.close();
		return buildings;
	}
	
	public void testIndex(File f) throws SQLException {
		Connection c = getConnection(f);
		try {
			for (City city : readCities(c)) {
				System.out.println("CITY " + city.getName()); //$NON-NLS-1$
				for (Street s : readStreets(c, city)) {
					System.out.println("\tSTREET " + s.getName()); //$NON-NLS-1$
					for (Building b : readBuildings(c, s)) {
						System.out.println("\t\tBULDING " + b.getName()); //$NON-NLS-1$
					}
				}

			}
		} finally {
			c.close();
		}
	}

}
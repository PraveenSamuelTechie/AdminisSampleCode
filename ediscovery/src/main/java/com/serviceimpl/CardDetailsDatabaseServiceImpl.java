package com.serviceimpl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;

import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.springframework.stereotype.Service;

import com.model.CreditCardDatabaseModel;
import com.model.DatabaseCredentialsModel;
import com.model.DatabaseScanStatsModel;
import com.service.CardDetailsDatabaseService;

/**
* eDiscovery (Web App) is a tool to detect credit card privacy information from local computer folders and databases.
*
* @author  Praveen Samuel Dhayalan
*/

@Service
public class CardDetailsDatabaseServiceImpl implements CardDetailsDatabaseService {

	//Variable declaration
    private static Connection con;
    private static Statement stmt = null;
    private static ResultSet rs = null;	
    
    private static DatabaseCredentialsModel cred;
    
    
    //Method to get Connection Details
	@Override
	public Connection getConnection(DatabaseCredentialsModel credentials) throws Exception{	       
		cred = credentials;
		Class.forName(credentials.getDriverName());            
        con = DriverManager.getConnection(credentials.getConnectionString(), credentials.getUserName(), credentials.getPassword());
        return con;
    }
	
	//Method to test the Connection
	@Override
	public Map<String,Object> TestDB(DatabaseCredentialsModel credentials)
	{
		Map<String,Object> testDbMap = new TreeMap<String,Object>();
		try
		{
			CardDetailsDatabaseService ser = new CardDetailsDatabaseServiceImpl();				
			con = ser.getConnection(credentials);
			stmt = con.createStatement();
			testDbMap.put("Success", "Connection Successful!");
		}
		catch(Exception ex)
		{
			//If it goes to the exception block, then there is an issue in the connection string
			System.out.println("Error in Test DB "+ex); 
			testDbMap.put("Fail", ex.getMessage());
		}
		return testDbMap;
	}
	
	//Gets the list of table names from the connection's meta-data.
	//Note: This also fetches system tables which should be ignored to improve performance.
	public List<String> getTableListFromMetaData()
	{
		List<String> tablesList = new ArrayList<String>();
		try
		{
		DatabaseMetaData  md = con.getMetaData();
        String[] types = {"TABLE","VIEW"};
		ResultSet rs = md.getTables(null, null, "%", types);
        while (rs.next()) {
        	try
        	{
            	tablesList.add(rs.getString("TABLE_NAME"));
        	}
        	catch(Exception e)
        	{
        		continue;
        	}
        }
		}
		catch(Exception e)
		{
			//Ignore
		}
		return tablesList;
	}
	
	//Scans the database and gives an overall analysis of the database
	@Override
	public List<DatabaseScanStatsModel> AnalyseDatabase(HttpSession session)
	{
		int tablesCount = 0;
		List<DatabaseScanStatsModel> dbScanStats = new ArrayList<DatabaseScanStatsModel>();
		String sql = null;
		List<String> tablesList = new ArrayList<String>();
		try {
            if(cred.getDatabaseType().toLowerCase().equals("oracle"))
            {
            	//Query to fetch table names from the database (Oracle Database)
            	sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE UPPER(OWNER) = '"+cred.getUserName().toUpperCase()+"' AND UPPER(OBJECT_TYPE) = 'TABLE'";
            	
            } else if(cred.getDatabaseType().toLowerCase().equals("mysql"))
            {
            	//Query to fetch table names from the database (MySQL Database)
            	sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA)='"+cred.getDatabaseName().toUpperCase()+"'";
            	
            } else if(cred.getDatabaseType().toLowerCase().equals("mssql"))
            {
            	//Query to fetch table names from the database (MSSQL Database)
            	sql = "SELECT NAME FROM SYS.TABLES WHERE TYPE_DESC = 'USER_TABLE'";
            }   
    		stmt = con.createStatement();
    		try
    		{
    			rs = stmt.executeQuery(sql);	
    		}
			catch(Exception e)
    		{
				//Just in-case if the above queries fail, table names will be fetched from the connection string's meta-data
				//Only drawback being it also fetches the system tables and there is no way to filter it out.
				tablesList = getTableListFromMetaData();
    		}
			while(null !=rs && !rs.isClosed() && rs.next())
			{
				try
				{
    				tablesList.add(rs.getString(1));
				}
				catch(Exception e)
				{
					continue;
				}
			}   			
			if(null == tablesList || (null != tablesList && tablesList.size() == 0))
			{
				//If the tablesList is empty, table names will be fetched from the connection string's meta-data
				//This is to make sure table names are populated
				tablesList = getTableListFromMetaData();
			}	
			//Iterate over the tables to obtain overall analytical information of the database
            for(String tableName : tablesList) {
            	int percentageBar = 0;
            	int totalTables = tablesList.size();
            	tablesCount++;
            	try
            	{            	
            		if(totalTables > 0)
    	        	{
            			//Formula to calculate the percentage
    		        	percentageBar = (int) (((float)((float)tablesCount)/((float)totalTables)) * 100);
    	        	}
            		session.setAttribute("DBAnalysisStatistics", tableName+","+percentageBar);
            		sql = "SELECT a.*, b.rowCnt FROM "+tableName+" a CROSS JOIN (SELECT COUNT(*) AS rowCnt FROM "+tableName+") b";
            		stmt = con.createStatement();
        			rs = stmt.executeQuery(sql);
        			ResultSetMetaData rsmd = rs.getMetaData();
        			int columnCount = rsmd.getColumnCount() - 1;
        			int rowCount = 0;
        			while(rs.next())
        			{
        				rowCount = rs.getInt("rowCnt"); 
        				break;
        			}        			   			
        			dbScanStats.add(new DatabaseScanStatsModel(tableName, columnCount, rowCount));            	
            	}
            	catch(Exception ex)
            	{
            		continue;
            	}
            }
		} catch(Exception ex)
    	{
    		System.out.println(ex);
    	}
		return dbScanStats;
	}
	
	@Override
	public List<CreditCardDatabaseModel> GetDatabaseMetaData(HttpSession session)
    {
	    long totalCellsCount = 0;
		long totalCells = (long) session.getAttribute("TotalCellsCount");
		int percentageBar = 0;
		List<CreditCardDatabaseModel> creditCardList = new ArrayList<CreditCardDatabaseModel>();
        try {
        	String sql = null;
    		List<String> tablesList = new ArrayList<String>();
    		try {
    			if(cred.getDatabaseType().toLowerCase().equals("oracle"))
                {
    				//Query to fetch table names from the database (Oracle Database)                	
                	sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE UPPER(OWNER) = '"+cred.getUserName().toUpperCase()+"' AND OBJECT_TYPE = 'TABLE'";
                	
                } else if(cred.getDatabaseType().toLowerCase().equals("mysql"))
                {
                	//Query to fetch table names from the database (MySQL Database)                	
                	sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA)='"+cred.getDatabaseName().toUpperCase()+"'";
                	
                } else if(cred.getDatabaseType().toLowerCase().equals("mssql"))
                {
                	//Query to fetch table names from the database (MSSQL Database)
                	sql = "SELECT NAME FROM SYS.TABLES WHERE TYPE_DESC = 'USER_TABLE'";
                }   
        		stmt = con.createStatement();
        		try
        		{
        			rs = stmt.executeQuery(sql);	
        		}
    			catch(Exception e)
        		{
    				//Just in-case if the above queries fail, table names will be fetched from the connection string's meta-data
    				//Only drawback being it also fetches the system tables and there is no way to filter it out.    				
    				tablesList = getTableListFromMetaData();
        		}
        		while(null !=rs && !rs.isClosed() && rs.next())
    			{
    				try
    				{
        				tablesList.add(rs.getString(1));
    				}
    				catch(Exception e)
    				{
    					continue;
    				}
    			}   			
    			if(null == tablesList || (null != tablesList && tablesList.size() == 0))
    			{
    				//If the tablesList is empty, table names will be fetched from the connection string's meta-data
    				//This is to make sure table names are populated    				
    				tablesList = getTableListFromMetaData();
    			}
    			//Loop 1 : Iterate over the table to read every cell
                for(String tableName : tablesList) {
                try
                {                  	
            		sql = "SELECT * FROM "+tableName;
            		try
            		{
            			stmt = con.createStatement();
            			rs = stmt.executeQuery(sql);
            		}
            		catch(Exception e)
            		{
            			System.out.println("Connection closed :"+e.getMessage());
            			TestDB(cred);
            			rs = stmt.executeQuery(sql);
            		}
        			ResultSetMetaData rsmd = rs.getMetaData();
        			int columnCount = rsmd.getColumnCount();
        			int rowCount = 0;
        			//Loop 2: Which iterates over every row of a table
        			while(rs.next())
        			{
        				rowCount ++;
        				try
                    	{
        				//Loop 3: Which iterates over every column of a table row
        				for(int i=1; i <= columnCount ;i++)
        				{
        					Object columnData;
        					String columnName = null;
        					try
        					{	        					        					
	        					columnName = rsmd.getColumnName(i);    
	        					columnData = rs.getObject(i);
	        					totalCellsCount++;
	        					if(totalCells > 0)
	            	        	{
	        						//Formula to calculate to table scan
	            		        	percentageBar = (int) (((float)((float)totalCellsCount)/((float)totalCells)) * 100);
	            	        	}
	        					session.setAttribute("DBScanStatistics",tableName+","+columnName+","+rowCount+","+i+","+totalCellsCount+","+percentageBar);
	        					if(null != columnData)
	        					{
	        						//Remove everything except Numbers
	        						columnData = String.valueOf(columnData).replaceAll("[\\s()-]", "");
	        						
	        						//Extracted cell data is passed on to extract credit card numbers from the cell data (if any)
	        						List<CreditCardDatabaseModel> credCardList = RegexChecker(rowCount, tableName, columnName, columnData);
	        						if(null != credCardList && credCardList.size() > 0)
		        					{ 
	        							//It is then passed on to an additional filter which checks if the number passes the Luhn Algorithm check 
	        							List<CreditCardDatabaseModel> filteredList = LuhnAlgorithmChecker(credCardList);
	        		            		if(null != filteredList && filteredList.size() > 0)
	        		            		{
	        		            			//All the filtered credit card numbers are stored to a list
	        		            			creditCardList.addAll(filteredList);		
	        		            		}  
		        					}
	        					}	        					
        					}
        					catch(Exception ex)
                        	{
        						System.out.println("Column Loop Error :"+ex.getMessage());
                        		continue;
                        	}
        				}
                    	}
        				catch(Exception ex)
                    	{
        					System.out.println("Row Loop Error :"+ex.getMessage());
                    		continue;
                    	}
        			}
            	} 
            	catch(Exception ex)
            	{
            		System.out.println("Table Loop Error :"+ex.getMessage());
            		continue;
            	}
            }
        } 
            catch (SQLException e) {
            e.printStackTrace();
        }        
        
    }  catch (Exception e) {
        e.printStackTrace();
    }   finally {
        try { rs.close(); } catch (Exception e) {  System.out.println("Error during rs closure "+e.getMessage());  }
        try { con.close(); } catch (Exception e) {  System.out.println("Error during conn closure "+e.getMessage());  }
    }
    return creditCardList;
    }
	
	//Luhn Algorithm is used to filter credit card numbers in a attempt to reduce false positives
	//Reference : https://www.youtube.com/watch?v=PNXXqzU4YnM	
	public List<CreditCardDatabaseModel> LuhnAlgorithmChecker(List<CreditCardDatabaseModel> scannedFilesList)
	{
		List<CreditCardDatabaseModel> filteredList = new ArrayList<CreditCardDatabaseModel>();
		try
		{
			for(CreditCardDatabaseModel credCardDBModel : scannedFilesList)
			{
				if(null != credCardDBModel.getCardNumber() && credCardDBModel.getCardNumber().contains(","))
				{
					String[] creditCardArr = credCardDBModel.getCardNumber().split(",");
					StringBuilder sb = new StringBuilder();
					for(String creditCard : creditCardArr)
					{
						//Used Apache Commons Library Utility to calculate Luhn Algorithm
						if(LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(creditCard))
						{
							//If a cell data has more than one credit card number, it will be stored as a comma seperated values.
							sb.append(creditCard);
							sb.append(",");
						}
					}
					if(null != sb && sb.toString().length() > 0)
					{
						sb.replace(sb.length()-1, sb.length(), ".");
						credCardDBModel.setCardNumber(null);
						credCardDBModel.setCardNumber(sb.toString());
						filteredList.add(credCardDBModel);
					}					
				}
				else if(null != credCardDBModel.getCardNumber())
				{
					if(LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(credCardDBModel.getCardNumber()))
					{
						filteredList.add(credCardDBModel);
					}
				}
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		return filteredList;
	}
	
	//Regex Checker is used to filter credit card numbers in an attempt to reduce false positives
	//This function strips credit card numbers from every database table cell
	//The regex string will be move to a database. It is hard-coded as it is still under testing
	//I'm also thinking about replacing the Regex check with a bin-check WEB API as bin codes keeps changing frequently 
	public static List<CreditCardDatabaseModel> RegexChecker(int rowCount, String tableName, String columnName, Object content)
	{		
		StringBuilder sb = new StringBuilder();
		List<CreditCardDatabaseModel> creditCardList = new ArrayList<CreditCardDatabaseModel>();
		Map<String, String> regexMap = new LinkedHashMap<String, String>();
		CreditCardDatabaseModel creditCardModel = null;
		regexMap.put("4[0-9]{12}(?:[0-9]{3})?","Visa");
		regexMap.put("(?:5[1-5][0-9]{2}|222[1-9]|22[3-9][0-9]|2[3-6][0-9]{2}|27[01][0-9]|2720)[0-9]{12}","Master");
		regexMap.put("(5018|5020|5038|6304|6759|6761|6763)[0-9]{8,15}","Maestro");
		regexMap.put("3[47][0-9]{13}","American Express");
		regexMap.put("3(?:0[0-5]|[68][0-9])[0-9]{11}","Diners Club");
		regexMap.put("6011\\d{12}|65\\d{14}|64[4-9]\\d{13}|622(1(2[6-9]|[3-9]\\d)|[2-8]\\d{2}|9([01]\\d|2[0-5]))\\d{10}","Discover");
		regexMap.put("(?:2131|1800|35\\d{3})\\d{11}","JCB 15 Digit");
		regexMap.put("(3(?:088|096|112|158|337|5(?:2[89]|[3-8][0-9]))\\d{12})","JCB 16 Digit");
		regexMap.put("(^(2014)|^(2149))\\d{11}","enRoute");
		regexMap.put("8699[0-9]{11}","Voyager");		
		
		//Optional		
		regexMap.put("63[7-9][0-9]{13}","Insta Payment");
		regexMap.put("(62[0-9]{14,17})","Union Pay");
		regexMap.put("(4903|4905|4911|4936|6333|6759)[0-9]{12}|(4903|4905|4911|4936|6333|6759)[0-9]{14}|(4903|4905|4911|4936|6333|6759)[0-9]{15}|564182[0-9]{10}|564182[0-9]{12}|564182[0-9]{13}|633110[0-9]{10}|633110[0-9]{12}|633110[0-9]{13}","Switch");	
		regexMap.put("(6334|6767)[0-9]{12}|(6334|6767)[0-9]{14}|(6334|6767)[0-9]{15}","Solo");	
		regexMap.put("(6304|6706|6709|6771)[0-9]{15}","Laser 19 Digit");		
		regexMap.put("(6304|6706|6709|6771)[0-9]{14}","Laser 18 Digit");		
		regexMap.put("(6304|6706|6709|6771)[0-9]{13}","Laser 17 Digit");
		regexMap.put("(6304|6706|6709|6771)[0-9]{12}","Laser 16 Digit");
		regexMap.put("9[0-9]{15}","Korean Local");
		regexMap.put("(6541|6556)[0-9]{12}","BCGlobal");
		regexMap.put("389[0-9]{11}","Carte Blanche");
		
		try
		{
		if(null != content && content.toString().length() > 0)
		{
		for(String regex : regexMap.keySet())
		{
			try
			{
			sb = new StringBuilder();
			Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		    Matcher matcher = p.matcher((CharSequence) content);			
		    while(matcher.find()) {
			if(sb.toString().length() > 0)
			{
				sb.append(",");		
			}
			sb.append(matcher.group());				
		    }
		    if(null != sb && sb.toString().length() > 0)
		    {
		    	creditCardModel = new CreditCardDatabaseModel(rowCount, regexMap.get(regex), sb.toString(), tableName, columnName);
		    	creditCardList.add(creditCardModel);
		    }
			}
			catch(Exception ex)
			{
				if(null !=creditCardModel)
				{
					System.out.println("Error in RegexChecker database Table Name: "+creditCardModel.getTableName() +" , Column Name :"+creditCardModel.getColumnName()+" , Row Number :"+creditCardModel.getId());
				}
				continue;
			}
		}   
		}
		}
		catch(Exception ex)
		{
			System.out.println("Error in RegexChecker database outerblock "+ex.getMessage());			
		}
	    return creditCardList;
	}		
	}

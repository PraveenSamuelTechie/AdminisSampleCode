package com.serviceimpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.HSLFSlideShow;
import org.apache.poi.hslf.model.Slide;
import org.apache.poi.hslf.model.TextRun;
import org.apache.poi.hslf.usermodel.SlideShow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xslf.usermodel.DrawingParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import com.aspose.ocr.ImageStream;
import com.aspose.ocr.OcrEngine;
import com.model.CardFinderModel;
import com.service.CardDetailsLocalService;

/**
* eDiscovery (Web App) is a tool to detect credit card privacy information from local computer folders and databases.
*
* @author  Praveen Samuel Dhayalan
*/

@Service
public class CardDetailsLocalServiceImpl implements CardDetailsLocalService {
	
	//Variable declaration
	public int folderCount = 0;
	public int fileCount = 0;
	public int tempCount = 0;
	public int percentageBar = 0;
	public int totalFilesCount = 1;

	@Override
	//Functionality to fetch the drive letters of a local computer
	public List<String> getLocalDrives() {
		folderCount = 0;
		fileCount = 0;
		tempCount = 0;
		percentageBar = 0;
		totalFilesCount = 1;
		List<String> drivesList = new ArrayList<String>();
		File[] rootDrive = File.listRoots();

		   for(File sysDrive : rootDrive){
			   drivesList.add(sysDrive.getPath());
		   }
		   return drivesList;
	}
	
	@Override	
		//Functionality to fetch the total number of files belonging to a root path (All files residing in sub-folders (n-level))
		public int getFilesCount(Path dir) {		
		folderCount = 0;
		fileCount = 0;
		percentageBar = 0;
		    try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
		        for (Path path : stream) {	        	
		        	try
		        	{
		        		if(path.toFile().isDirectory()) {
		        	    	getFilesCount(path);
			            } 
		        		else 
		        		{	
		        			totalFilesCount++;
			            }
			        }
		            catch(Exception ex)
		            {
		            	continue;
		            }
		        	finally
		        	{
		        		
		        	}
		        }
		        stream.close();
		    } catch(IOException e) {
		    }	    
		    return totalFilesCount;
	} 	
	
	@Override 
	//Luhn Algorithm is used to filter credit card numbers in a attempt to reduce false positives
	//Reference : https://www.youtube.com/watch?v=PNXXqzU4YnM		
	public List<CardFinderModel> LuhnAlgorithmChecker(List<CardFinderModel> scannedFilesList)
	{
		List<CardFinderModel> filteredList = new ArrayList<CardFinderModel>();
		try
		{
			for(CardFinderModel cardFinderModel : scannedFilesList)
			{
				if(null != cardFinderModel.getCardNumber() && cardFinderModel.getCardNumber().contains(","))
				{
					String[] creditCardArr = cardFinderModel.getCardNumber().split(",");
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
						cardFinderModel.setCardNumber(null);
						cardFinderModel.setCardNumber(sb.toString());
						filteredList.add(cardFinderModel);
					}					
				}
				else if(null != cardFinderModel.getCardNumber())
				{
					if(LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(cardFinderModel.getCardNumber()))
					{
						filteredList.add(cardFinderModel);
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
	
	
	//Functionality to fetch credit card list
	@Override
	public List<CardFinderModel> getCreditCardList(List<CardFinderModel> scannedFilesList, Path dir, String[] extensions, long totalFiles, HttpSession session) {		
	    //Used directory streamer to load a list of files
		//Directory streamer buffers one file path at a time instead loading all files into memory
		try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
	        for (Path path : stream) {
	        	try
	        	{
		        	if(totalFiles > 0)
		        	{
			        	percentageBar = (int) (((float)((float)fileCount)/((float)totalFiles)) * 100);
		        		
			        	//Formula to calculate scan percentage
			        	
			        	/*percentageBar = (int) (((float)((float)tempCount)/((float)totalFiles)) * 100);			       
		        		
		        		if(percentageBar >= 100)
		        		{
		        			tempCount = 0;
		        			percentageBar = 0;
		        		}*/
		        	}	        	
	        		if(path.toFile().isDirectory()) {
	        	    	folderCount++;
	        	    	//Recursive function coupled with a directory streamer
	        	    	getCreditCardList(scannedFilesList, path, extensions,totalFiles, session);
		                session.setAttribute("scanStatistics",path.toFile().getAbsolutePath()+","+ folderCount +","+ fileCount +","+ percentageBar);
		            } else {
		            	fileCount++;
		            	tempCount++;
		            	session.setAttribute("scanStatistics",path.toFile().getAbsolutePath()+","+ folderCount +","+ fileCount +","+ percentageBar);
			            String fileExtension = FilenameUtils.getExtension(path.getFileName().toString().toLowerCase());
		            	if(Arrays.asList(extensions).contains(fileExtension))
		            	{
		            		//It is passed on to an additional filter which checks if the number passes the Luhn Algorithm check 							
		            		List<CardFinderModel> filteredList = LuhnAlgorithmChecker(scanFiles(path.toAbsolutePath().toString()));
		            		if(null != filteredList && filteredList.size() > 0)
		            		{
			            		scannedFilesList.addAll(filteredList);		
		            		}        		
		            	}
		            }
	        	}
	            catch(Exception ex)
	            {
	            	continue;
	            }
	        	finally
	        	{
	        		
	        	}
	        }
	        stream.close();
	    } catch(IOException e) {
	    }	    
	    return scannedFilesList;
	} 	
	
	//Older method which loads all the files into memory
	//To be removed
	@Override
	public List<File> getFiles(String directory, String[] extensions, Boolean recursive) {
		List<File> files = null;
		try
		{		
		File dir = new File(directory);		
		files = (List<File>) FileUtils.listFiles(dir, extensions, true);
		}
		catch(Exception ex)
		{
			System.out.println("Error in getFiles: " + ex.getMessage());
		}
		return files;
	}

	//Function to scan different file types and it extract credit card information using regex
	//File types that are being scanned are doc, docx, xls, xlsx, ppt, rtf and all other plain text file extensions
	@Override
	public List<CardFinderModel> scanFiles(String filePath) {
		List<CardFinderModel> creditCardList = new ArrayList<CardFinderModel>();
		try
		{			
				String extension = FilenameUtils.getExtension(filePath);
				String content = null;
				try				
				{
					if(null != extension && extension.equalsIgnoreCase("doc"))
					{
						content = ReadWordDoc(filePath);
						
					} else if(extension.equalsIgnoreCase("docx"))
					{
						content = ReadWordDocx(filePath);
					}
					else if(extension.equalsIgnoreCase("xls"))
					{
						content = ReadXLS(filePath);
					}
					else if(extension.equalsIgnoreCase("xlsx"))
					{
						content = ReadXLSX(filePath);
					}
					else if(extension.equalsIgnoreCase("ppt"))
					{
						content = ReadPPT(filePath);
					}
					else if(extension.equalsIgnoreCase("pptx"))
					{
						content = ReadPPTX(filePath);
					}
					else if(extension.equalsIgnoreCase("rtf"))
					{
						content = ReadRTF(filePath);
					}	
					else if(extension.equalsIgnoreCase("pdf"))
					{
						content = ReadPDF(filePath);
					}
					else if(extension.equalsIgnoreCase("JPEG") ||
							extension.equalsIgnoreCase("JPG") ||
							extension.equalsIgnoreCase("PNG")  ||
							extension.equalsIgnoreCase("GIF")  ||
							extension.equalsIgnoreCase("BMP")  ||
							extension.equalsIgnoreCase("TIFF"))
					{
						content = ReadImage(filePath);
					}
					else
					{
						content = ReadText(filePath);						
					}	
					//Extracted data passed on to a regex function to strip credit card numbers from the contents (if any)					
					creditCardList.addAll(RegexChecker(filePath, content));
				}
				catch(Exception ex)
				{
					System.out.println("Error in scanFiles " + ex.getMessage());
				}
		}
		catch(Exception ex)
		{
			System.out.println("Error in getFiles "+ex);
		}		
		return creditCardList;
	}
	
	@Override
	//Function to read plain text data
	public String ReadText(String location)
	{
		String content = null;
        StringBuilder sb = new StringBuilder();
		try(BufferedReader br = new BufferedReader(new FileReader(location))) {
	        String line;
	        while ((line = br.readLine()) != null) {
	        	try
	        	{	        		
	        		if(null != sb && sb.toString().length() < 2000000)
	        		{
	        			//remove all characters else except numbers
	        			line = line.replaceAll("[\\s()-]", "");
		        		sb.append(line);	 
	        		}
	        		else
	        		{
	        			System.out.println("Break out of ReadText");
	        			break;
	        		}
	        	}
	        	catch(Exception ex)
	        	{
	    			System.out.println("Error in Read Text broke out of the loop ");
	        		break;
	        	}
	        }
	        content = sb.toString();
		}
		catch(Exception ex)
		{
			System.out.println("Error in Read Text new method "+ex.getMessage());
		}
		return content;
	}
	
	//Function to read docx format
	@Override
	public String ReadWordDocx(String location)
	{
		StringBuilder sb = new StringBuilder();
		try {
            File file = new File(location);
            FileInputStream fis = new FileInputStream(file.getAbsolutePath());
            XWPFDocument document = new XWPFDocument(fis);
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph para : paragraphs) {
            	try
            	{
            		if(null != sb && sb.toString().length() < 2000000)
	        		{
            		String paragraph = para.getText();
            		//Removes all characters except numbers
            		paragraph = paragraph.replaceAll("[\\s()-]", "");
            		sb.append(paragraph);
	        		}
            		else
            		{
            			System.out.println("Break out of ReadWordDocx");
            			break;
            		}
            	}
            	catch (Exception e) {
                    System.out.println("Error in Read Word Doc Loop");
                    continue;
                }
            }
            fis.close();
        } catch (Exception e) {
            System.out.println("Error in Read Word Docx "+e.getMessage());
        }
		return sb.toString();
	}
	
	//Function to read doc format
	@Override
	public String ReadWordDoc(String location)
	{	
		StringBuilder sb = new StringBuilder();
		String[] fileDataArr = null;
		try {
            File file = new File(location);
            FileInputStream fis = new FileInputStream(file.getAbsolutePath());
            HWPFDocument document = new HWPFDocument(fis);
            WordExtractor extractor = new WordExtractor(document);
            fileDataArr = extractor.getParagraphText();
            for (String para : fileDataArr) {
            	try
            	{
            		if(null != sb && sb.toString().length() < 2000000)
	        		{
            		String paragraph = para;
            		//Removes all characters exception numbers
            		paragraph = paragraph.replaceAll("[\\s()-]", "");
            		sb.append(paragraph);
	        		}
            		else
            		{
            			System.out.println("Break out of ReadWordDoc");
            			break;
            		}
            	}
            	catch (Exception e) {
                    System.out.println("Error in Read Word Doc Loop");
                    continue;
                }
			}
            fis.close();
        } catch (Exception e) {
            System.out.println("Error in Read Word Doc ");
        }
		return sb.toString();
	}
	
	//Function to read xlsx format
	@Override
	public String ReadXLSX(String location)
	{
		StringBuilder sb = new StringBuilder();
		try
        {
            FileInputStream file = new FileInputStream(new File(location));
 
            //Create Workbook instance holding reference to .xlsx file
            XSSFWorkbook workbook = new XSSFWorkbook(file);
 
            for(int i=0; i < workbook.getNumberOfSheets(); i++)
            {
            	try
            	{
            		XSSFSheet sheet = workbook.getSheetAt(i);
               	 
                    //Iterate through each rows one by one
                    Iterator<Row> rowIterator = sheet.iterator();
                    while (rowIterator.hasNext()) 
                    {
                        Row row = rowIterator.next();
                        //For each row, iterate through all the columns
                        Iterator<Cell> cellIterator = row.cellIterator();
                         
                        while (cellIterator.hasNext()) 
                        {
                        	if(null != sb && sb.toString().length() < 2000000)
        	        		{
                            Cell cell = cellIterator.next();
                            String paragraph = cell.getStringCellValue();
                            //Removes all characters exception numbers
                    		paragraph = paragraph.replaceAll("[\\s()-]", "");
                            sb.append(paragraph);
        	        		}
                        	else
                    		{
                        		System.out.println("Break out of ReadXLSX");
                    			break;
                    		}
                        }
                    }
            	}  
            	catch(Exception ex)
            	{
            		continue;
            	}
            }            
            file.close();
        } 
        catch (Exception e) 
        {
            System.out.println("Error in Read XLSX "+e);
        }
		
		return sb.toString();
	}
	
	//Function to read xls format
	@Override
	public String ReadXLS(String location)
	{
		StringBuilder sb = new StringBuilder();
		try
        {
            FileInputStream file = new FileInputStream(new File(location));
 
            //Create Workbook instance holding reference to .xls file
            HSSFWorkbook workbook = new HSSFWorkbook(file);
 
            for(int i=0; i < workbook.getNumberOfSheets(); i++)
            {
            try
            {
            HSSFSheet sheet = workbook.getSheetAt(i); 
            //Iterate through each rows one by one
            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) 
            {
            	try
            	{
                Row row = rowIterator.next();
                //For each row, iterate through all the columns
                Iterator<Cell> cellIterator = row.cellIterator();
                 
                while (cellIterator.hasNext()) 
                {
                	try
                	{
                		if(null != sb && sb.toString().length() < 2000000)
    	        		{
                		 Cell cell = cellIterator.next();
                		 String paragraph = cell.getStringCellValue();
                 		 //remove all characters except numbers
                		 paragraph = paragraph.replaceAll("[\\s()-]", "");
                         sb.append(paragraph);
    	        		}
                		else
                		{
                			System.out.println("Break out of ReadXLS");
                			break;
                		}
                	}
                	catch(Exception ex)
                	{
                		continue;
                	}
                }
            	}
            	catch(Exception ex)
            	{
            		continue;
            	}
            }
            file.close();
            }
            catch(Exception ex)
            {
            	continue;
            }
            }
        } 
        catch (Exception e) 
        {
        	System.out.println("Error in Read XLS ");
        }
		return sb.toString();
	}
	
	//Function to read RTF file formats
	@Override
    public String ReadRTF(String location) throws Exception {
    	String content = null;
    	try
    	{
    		FileInputStream inputStream = new FileInputStream(new File(location));        	
            DefaultStyledDocument doc = new DefaultStyledDocument();
            new RTFEditorKit().read(inputStream, doc, 0);
            if(doc.getLength() > 2000000)
            {
            	content = doc.getText(0, 2000000);
            }
            else
            {
            	content = doc.getText(0, doc.getLength());
            }
            
            //Removes all characters except numbers
            content = content.replaceAll("[\\s()-]", "");
    	}
    	catch(Exception ex)
    	{
    		System.out.println("Error in Read RTF");
    	}
    	return content;
    }

	//Functionality to read PPT file formats
	@Override
    public String ReadPPT(String path)
    {
    StringBuilder sb = new StringBuilder();
	    try {
	    File file=new File(path);
	    FileInputStream fis = new FileInputStream(file);
	    POIFSFileSystem fs = new POIFSFileSystem(fis);
	    HSLFSlideShow show = new HSLFSlideShow(fs);
	    SlideShow ss = new SlideShow(show);
	    Slide[] slides=ss.getSlides();
	    for (int x = 0; x < slides.length; x++) {
	    try
	    {
	    	TextRun[] runs = slides[x].getTextRuns();
		    for (int i = 0; i < runs.length; i++) {
		    TextRun run = runs[i];   
		    if(null != sb && sb.toString().length() < 2000000)
    		{
		    String paragraph = run.getText().toString();
    		//Removes all characters except numbers
		    paragraph = paragraph.replaceAll("[\\s()-]", "");
            sb.append(paragraph);
    		}
		    else
		    {
		    	System.out.println("Break out of ReadPPT");
		    	break;		    	
		    }
		    }
	    }
	    catch(Exception ex)
	    {
	    	continue;
	    }
	    }
	    } catch (Exception ex) {
	    	System.out.println("Error in Read PPT");
	    }
	    return sb.toString();
    }

	//Functionality to read PPTX file formats
	@Override
    public String ReadPPTX(String location)
    {
    	StringBuilder sb = new StringBuilder();
	    try
	    {
	    XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(location));
	    XSLFSlide[] slides = ppt.getSlides();	
	    for(int i=0;i<slides.length;i++)
	    {
	    try
	    {
	    XSLFSlide slide = slides[i];
	    List<DrawingParagraph> data = slide.getCommonSlideData().getText();	
	    for(int j=0;j<data.size();j++)
	    {
	    try
	    {
	       if(null != sb && sb.toString().length() < 2000000)
	       {
	    	DrawingParagraph drawingParagraph = data.get(j);
	    	String paragraph = (String) drawingParagraph.getText().toString();
    		//Removes all characters exception numbers
	    	paragraph = paragraph.replaceAll("[\\s()-]", "");
            sb.append(paragraph);
	       }
	       else
	       {
	    	   System.out.println("Break out of ReadPPTX");
		    	break;	
	       }
	    }
	    catch(Exception ex)
	    {
	    	continue;
	    }
	    }
	    }
	    catch(Exception ex)
	    {
	    	continue;
	    }
	    }
	    }catch(Exception ex)
	    {
	    	System.out.println("Error in Read PPTX "+ex);
	    }
	    return sb.toString();
    }
    
	//Functionality to read PDF file formats	
	@Override
    public String ReadPDF(String location)
    {
		String content = null;
    	try {    		   		
    		  //Loading an existing document
    	      File file = new File(location);
    	      PDDocument document = PDDocument.load(file);  
    	      if(null != document && !document.isEncrypted())
    	      {    	    
    	      //Instantiate PDFTextStripper class
    	      int pageNos = document.getNumberOfPages();
    	      
    	      PDFTextStripper pdfStripper = new PDFTextStripper();
    	      //Setting the maximum page size to 500 to improve performance
    	      if(pageNos > 500)
    	      {
    	    	  pdfStripper.setEndPage(500);
    	      }
    	      //Retrieving text from PDF document page wise
    	      content = pdfStripper.getText(document);    	    
      		  //Removes all characters exception numbers
    	      content = content.replaceAll("[\\s()-]", "");
    	      //Closing the document
    	      document.close();    
    	      }
    	         	 
    	} catch (Exception ex) {
    		System.out.println("Error in Read RDF "+ex);
    	}	    	
    	return content;
    }
	
	//Functionality to read images files
	//This is stil under research stage
	@Override
	public String ReadImage(String location)
	{	

		// Create an instance of OcrEngine
		OcrEngine ocr = new OcrEngine();
		String content = null;
		// Set image file
		ocr.setImage(ImageStream.fromFile(location));

		// Perform OCR and get extracted text
		try {
			if (ocr.process()) {
				System.out.println("\ranswer -> " + ocr.getText());
				content = ocr.getText().toString();
				content = content.replaceAll("[\\s()-]", "");
			}
		} catch (Exception e) {
			System.out.println("Error in ReadImage");
	}
	return content;	
	}
    
	//Regex Checker is used to filter credit card numbers in an attempt to reduce false positives
	//This function strips credit card numbers from every database table cell
	//The regex string will be move to a database. It is hard-coded as it is still under testing
	//I'm also thinking about replacing the Regex check with a bin-check WEB API as bin codes keeps changing frequently 		
	@Override
	public List<CardFinderModel> RegexChecker(String location, String content)
	{
		StringBuilder sb = new StringBuilder();
		List<CardFinderModel> creditCardList = new ArrayList<CardFinderModel>();
		CardFinderModel creditCardInfo = null;
		
		Map<String, String> regexMap = new LinkedHashMap<String, String>();
		
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
		if(null != content && content.length() > 0)
		{
		for(String regex : regexMap.keySet())
		{
			try
			{
				sb = new StringBuilder();
				Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			    Matcher matcher = p.matcher(content);			
			    while(matcher.find()) {
				if(null != sb && sb.toString().length() > 0)
				{
					sb.append(",");		
				}
				sb.append(matcher.group());				
			    }
			    if(null != sb && sb.toString().length() > 0)
			    {
			    	creditCardInfo = new CardFinderModel(null, regexMap.get(regex), sb.toString(), location);		    	
			    	creditCardList.add(creditCardInfo);
			    }
			}
			catch(Exception ex)
			{
				System.out.println("Error in RegexChecker for file location "+location + ", Error Message :"+ex.getMessage());
				continue;
			}
		} 
		}
		}
		catch(Exception ex)
		{
			System.out.println("Error in RegexChecker file scan outerblock "+ex.getMessage());
		}
	    return creditCardList;
	}	
	
	//Functionality to get network drives mapped to a local computer
	//This is still under research stage
	@Override
	public List<String> getNetworkDrives()
	{
		List<String> pathList = new ArrayList<String>();		
		
		StringBuilder sb = new StringBuilder();
		String content = null;
		try
		{			
			 Runtime runTime = Runtime.getRuntime();
			 //Executes "net use" command through command prompt which lists the networks folders connected to a local computer
			 Process process = runTime.exec("net use");
			 InputStream inStream = process.getInputStream();
			 InputStreamReader inputStreamReader = new InputStreamReader(inStream);
			 BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			 String newLine = null;
			 while(null != (newLine = bufferedReader.readLine()))
			 {
				 sb.append(newLine);
			 }
			 content = sb.toString();
			
			 	String[] contentArr = content.split(" ");
			 	for(String contentStr : contentArr)
			 	{
			 		String networkFolderRegex = "\\\\.*\\$";
					Pattern p = Pattern.compile(networkFolderRegex,Pattern.CASE_INSENSITIVE);
				    Matcher matcher = p.matcher(contentStr);			
				    while(matcher.find()) {			
					pathList.add(matcher.group());
			 	}			 	
			    }			 
		}
		catch(Exception ex)
		{
			System.out.println("Error in Network Path "+ex);
		}
		return pathList;
	}
}
    

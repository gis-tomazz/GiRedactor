package iText;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNameTree;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IPdfTextLocation;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import com.itextpdf.pdfcleanup.PdfCleanUpLocation;
import com.itextpdf.pdfcleanup.PdfCleanUpTool;
import com.itextpdf.pdfcleanup.autosweep.PdfAutoSweep;
import com.itextpdf.pdfcleanup.autosweep.RegexBasedCleanupStrategy;

public class GiRedactor {
	public static class Bookmark{
        public int pageNumber=-1;
        public String title="";
        List<Bookmark> children;
        
        public Bookmark(int pageNumber, String title) {
        	this.pageNumber=pageNumber;
        	this.title=title;
        	this.children=new ArrayList<Bookmark>();
        }
        
        public void addChild(Bookmark e) {
        	this.children.add(e);
        }
    }
	
	public static Bookmark walkOutlines(PdfOutline outline, Map<String, PdfObject> names, PdfDocument pdfDocument, Bookmark parent) {
    	String title="";
    	int pageNumber=-1;
        if (outline.getDestination() != null) {
        	title=outline.getTitle();
            pageNumber=pdfDocument.getPageNumber((PdfDictionary) outline.getDestination().getDestinationPage(names));
        }
        
        Bookmark bookMark=new Bookmark(pageNumber,title);
        
        if (parent!=null) parent.addChild(bookMark);
        
        for (PdfOutline child : outline.getAllChildren()) {
            walkOutlines(child, names, pdfDocument, bookMark);
        }
        
        return bookMark;
    }
    
    public static void rebuildOutline(PdfOutline outline, Bookmark bookMark) {
    	for (Bookmark bm : bookMark.children) {
    		PdfOutline subOutline=outline.addOutline(bm.title);
    		subOutline.addDestination(PdfExplicitDestination.createFit(bm.pageNumber));
    		subOutline.setOpen(false);
    		rebuildOutline(subOutline,bm);
    	}
    }
    
    public static boolean isEmso(String emso) {
    	if (emso.length()!=13) return false;
    	if (emso.compareTo("0000000000000")==0) return false;
    	int aEmso[] = new int[13];
    	
    	for (int i=0;i<13;++i) {
    		aEmso[i]=Integer.parseInt(Character.toString(emso.charAt(i)));
    	}
    	
    	///////////////kontrola datuma
    	int day=aEmso[0]*10+aEmso[1];
    	if (day>31 || day<1) return false;
    	
    	int month=aEmso[2]*10+aEmso[3];
    	if (month>12 || month<1) return false;
    	//////////////////////////////
    	
    	int fEmso[] = new int[] {7, 6, 5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    	
    	int emsoSum=0;
    	for (int i=0;i<12;++i) {
    		emsoSum=emsoSum+aEmso[i]*fEmso[i];
    	}
    	
    	int controlDigit=emsoSum % 11;
    	if (controlDigit!=0) controlDigit=11-controlDigit;    	
    	    	
    	return aEmso[12]==controlDigit;
    }
    
    /*
     * Vrne seznam lokacij EMŠO številk v PDF-ju z OCR slojem
     * 
     * @param input pot do vhodne datoteke, npr. c:\testi\mojDokument.pdf
     * 
     * @return seznam lokacij EMŠO številk
     */
    
    public static ArrayList<PdfCleanUpLocation> getCleanupLocations(String input) throws Exception {
  		PdfDocument pdf;
  	   	ArrayList<PdfCleanUpLocation> cleanupLocations=new ArrayList<PdfCleanUpLocation>();
  	   	
  	   	try {
  	   		pdf = new PdfDocument(new PdfReader(input));
  	   	} catch (Exception e) {
  			return cleanupLocations;
  		}
  	    		    
  	    int n = pdf.getNumberOfPages();
  	    
  	    for (int i = 1; i <= n; i++) {
  	        PdfPage page = pdf.getPage(i);
  	        	        
  	        RegexBasedCleanupStrategy strategy = new RegexBasedCleanupStrategy("[0123]\\d[01]\\d[089]\\d{8}");
  		    PdfAutoSweep autoSweep = new PdfAutoSweep(strategy);
  		    autoSweep.getPdfCleanUpLocations(page);
  		    Iterator<IPdfTextLocation> it=strategy.getResultantLocations().iterator();
  		    
  		    while (it.hasNext()){
  		    	IPdfTextLocation current=it.next();
  	        	String emso=current.getText();
  	        	if (isEmso(emso)) {
  	        		cleanupLocations.add(new PdfCleanUpLocation(i,current.getRectangle(),ColorConstants.BLACK));
  	        	}
              }
  	    }
  	    
  	    pdf.close();
		
  	    return cleanupLocations;
     }
    
    /*
     * Zakrije EMŠO v PDF-ju z OCR slojem
     * 
     * @param input pot do vhodne datoteke,  npr. c:\testi\mojDokument.pdf
     * @param output ime izhodne datoteke (če že obstaja, bo prepisana; če je odprta bom vrgel exception),  npr. c:\output\mojDokument_redacted.pdf
     * @param tempFolder pot do mape, kamor Ghostscript shranjuje začasne datoteke - funkcija gsConvert po uporabi te začasne datoteke zbriše (POZOR!!! pot se mora končati z backslashom), npr.: c:\temp\
     * @param gsbat pot do batch datoteke (npr.: "c:\\bin\\gs.bat") z naslednjo vsebino (prilagodi pot do Ghostscripta v batch datoteki glede na svoj sistem): "C:\Program Files\gs\gs9.23\bin\gswin64c.exe" -dFirstPage=%1 -dLastPage=%2 -dSAFER -dBATCH -dNOPAUSE -dNOCACHE -sDEVICE=pdfwrite -dPDFSETTINGS=/prepress -sColorConversionStrategy=/LeaveColorUnchanged -dAutoFilterColorImages=true -dAutoFilterGrayImages=true -dDownsampleMonoImages=false -dDownsampleGrayImages=false -dDownsampleColorImages=false -sOutputFile=%4 %3 2> nul
     * 
     * @return tab separated podatki o zakrivanju (baseName datoteke, porabljen čas za zakrivanje, število strani z emšo, seznam strani z emšo, velikost datoteke pred zakrivanjem, velikost datoteke po zakrivanju oziroma "0", če v dokumentu ne najde EMŠO 
     *   
     */
    
    public static String gsConvert(String input, String output, String tempFolder, String gsbat) throws Exception {
    	long start = System.currentTimeMillis();
    	List<PdfCleanUpLocation> cleanupLocations=getCleanupLocations(input);
    	int locationCount=cleanupLocations.size();
    	if (locationCount==0) return "0";
    	
   		PdfDocument pdf = new PdfDocument(new PdfReader(input),new PdfWriter(output));
   	   	
   	   	PdfNameTree destsTree=pdf.getCatalog().getNameTree(PdfName.Dests);
   	   	PdfOutline root = pdf.getOutlines(false);
   	   	Bookmark origOutline=walkOutlines(root, destsTree.getNames(), pdf, null);	// shrani outline, da ga pozneje rebuildaš
   	   	
   	   	File f = new File(input);
   	   	String baseName=f.getName();
   	   	
   	   	LinkedHashSet<Integer> pageList=new LinkedHashSet<>();
   	   	String pageListString="";
   	   	for (int i=0;i<locationCount;++i) {
   	   		PdfCleanUpLocation loc=cleanupLocations.get(i);
   	   		if (pageList.add(loc.getPage())) pageListString+=loc.getPage()+",";
   	   	}
   	   	
   	   	if (!pageListString.isEmpty()) pageListString = "\""+pageListString.substring(0, pageListString.length() - 1)+"\"";
   	   	    	
    	//"C:\Program Files\gs\gs9.23\bin\gswin64c.exe" -dFirstPage=%1 -dLastPage=%2 -dSAFER -dBATCH -dNOPAUSE -dNOCACHE -sDEVICE=pdfwrite -dPDFSETTINGS=/prepress -sColorConversionStrategy=/LeaveColorUnchanged -dAutoFilterColorImages=true -dAutoFilterGrayImages=true -dDownsampleMonoImages=false -dDownsampleGrayImages=false -dDownsampleColorImages=false -sOutputFile=%4 %3 2> nul
    	//"C:\Program Files\gs\gs9.23\bin\gswin64c.exe" -sPageList=%1 -dSAFER -dBATCH -dNOPAUSE -dNOCACHE -sDEVICE=pdfwrite -dPDFSETTINGS=/prepress -sColorConversionStrategy=/LeaveColorUnchanged -dAutoFilterColorImages=true -dAutoFilterGrayImages=true -dDownsampleMonoImages=false -dDownsampleGrayImages=false -dDownsampleColorImages=false -sOutputFile=%3 %2 2> nul

   	 	PdfCleanUpTool cleaner = new PdfCleanUpTool(pdf);
    	
    	PdfObject dict=pdf.getCatalog().getPdfObject().get(PdfName.Outlines);
    	pdf.getCatalog().getPdfObject().put(PdfName.Outlines, null);
    	pdf.getOutlines(true);
    	
    	pageList=new LinkedHashSet<>();
    	for (int i=0;i<locationCount;++i) {
   	   		PdfCleanUpLocation loc=cleanupLocations.get(i);
   	   		int pnum=loc.getPage();
   	   		if (pageList.add(pnum)) {
   	   			String tmpFileName=tempFolder+UUID.randomUUID()+"_"+baseName;
	   	   		String[] CMD_ARRAY = {gsbat,pnum+"",pnum+"", input, tmpFileName };
	   	    	ProcessBuilder pb = new ProcessBuilder(CMD_ARRAY);
	   	    	Process p = pb.start(); // Start the process.
	   	    	p.waitFor(); // Wait for the process to finish.
	   	    	
	   	    	PdfDocument r1 = new PdfDocument(new PdfReader(tmpFileName));
	   	    	PdfOutline r1root=r1.getOutlines(false);
	   	    	if (r1root!=null) r1root.getAllChildren().clear();	//clear outlines, sicer na koncu itak rebuildam, ampak vseeno ...
   	   			pdf.removePage(pnum);
   	   	   	 	r1.copyPagesTo(1,1, pdf,pnum);
   	    	 	r1.close();
   	    	 	File f1=new File(tmpFileName);
   	    	 	f1.delete();
   	   		}
   	   	 	cleaner.addCleanupLocation(loc);
   	   	}
   	 	
   	 	pdf.getCatalog().getPdfObject().put(PdfName.Outlines, dict);
    	   	 	   	    
   	 	cleaner.cleanUp();
   	    
   	 	root.getAllChildren().clear();
   	 	rebuildOutline(root,origOutline);
   	 	
   	    pdf.close();
   	    
    	long finish = System.currentTimeMillis();
    	long timeElapsed = finish - start;
    	String sizeBefore=String.format("%.2f",(double)f.length()/(1024.0*1024.0));
    	f=new File(output);
    	String sizeAfter=String.format("%.2f",(double)f.length()/(1024.0*1024.0));
    	return baseName+"\t"+timeElapsed+"\t"+pageList.size()+"\t"+pageListString+"\t"+sizeBefore+"\t"+sizeAfter;
      }
}

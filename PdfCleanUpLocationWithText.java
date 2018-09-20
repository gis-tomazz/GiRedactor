package iText;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.pdfcleanup.PdfCleanUpLocation;

/* helper class, da shranim zraven lokacije še emšo */

public class PdfCleanUpLocationWithText extends PdfCleanUpLocation {
	public String _text;
	
	public PdfCleanUpLocationWithText(int page, Rectangle region, Color cleanUpColor, String text) {
		super(page, region, cleanUpColor);
		this._text=text;
	}

}

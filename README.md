# GiRedactor

iText7 redacting "Unique Master Citizen Number" https://en.wikipedia.org/wiki/Unique_Master_Citizen_Number.

## Requirements

- iText7 (see the pom.xml https://github.com/gis-tomazz/GiRedactor/blob/master/pom.xml)
- Ghostscript (https://github.com/gis-tomazz/GiRedactor/blob/master/GiRedactor.java#L151)

## How to use it

```
String input="c:\\test\\test_in.pdf";
String output="c:\\test\\test_out.pdf";
String tmpFolder="c:\\temp\\";
String gsbat="c:\\bin\\gs.bat";

GiRedactor.gsConvert(input,output,tempFolder,gsbat);

```

See the gsConvert function description in the code: https://github.com/gis-tomazz/GiRedactor/blob/master/GiRedactor.java#L145



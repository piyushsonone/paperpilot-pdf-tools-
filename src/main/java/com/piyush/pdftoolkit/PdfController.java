package com.piyush.pdftoolkit;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {
    private static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    private static final int MAX_FILES = 20;

    @PostMapping("/merge")
    public ResponseEntity<byte[]> merge(@RequestParam("files") List<MultipartFile> files) throws IOException {
        validateFiles(files, 2);
        PDFMergerUtility merger = new PDFMergerUtility(); ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (MultipartFile f : files) merger.addSource(new ByteArrayInputStream(f.getBytes()));
        merger.setDestinationStream(out); merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
        return pdfResponse(out.toByteArray(), "merged.pdf");
    }

    @PostMapping("/compress")
    public ResponseEntity<byte[]> compress(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue="recommended") String level,
            @RequestParam(required=false) Double targetMb) throws IOException {
        validateFile(file);
        if (!Set.of("light", "recommended", "strong").contains(level)) throw new IllegalArgumentException("Choose a valid compression level.");
        byte[] original = file.getBytes();
        try (PDDocument doc = PDDocument.load(original); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle(""); doc.getDocumentInformation().setAuthor("");
            doc.getDocumentInformation().setSubject(""); doc.getDocumentInformation().setKeywords("");
            doc.getDocumentInformation().setCreator(""); doc.getDocumentInformation().setProducer("PDF Toolkit");
            doc.save(out); byte[] cleaned = out.toByteArray();
            // Preserve searchable text whenever the metadata/structure pass achieves a smaller file.
            if (cleaned.length < original.length && (targetMb == null || cleaned.length <= targetMb * 1024 * 1024))
                return pdfResponse(cleaned, "compressed.pdf");
        }
        float dpi = switch(level) { case "light" -> 110f; case "strong" -> 65f; default -> 85f; };
        float quality = switch(level) { case "light" -> .78f; case "strong" -> .48f; default -> .62f; };
        byte[] best = rasterCompress(original, dpi, quality);
        if (targetMb != null && targetMb > 0 && best.length > targetMb * 1024 * 1024) {
            for (int i=0; i<4 && best.length > targetMb * 1024 * 1024; i++) {
                dpi = Math.max(35f, dpi * .78f); quality = Math.max(.25f, quality - .09f);
                byte[] candidate = rasterCompress(original, dpi, quality);
                if (candidate.length < best.length) best = candidate;
            }
        }
        return pdfResponse(best, "compressed.pdf");
    }

    private byte[] rasterCompress(byte[] source, float dpi, float quality) throws IOException {
        try (PDDocument input = PDDocument.load(source); PDDocument output = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFRenderer renderer = new PDFRenderer(input);
            for (int i=0; i<input.getNumberOfPages(); i++) {
                BufferedImage rendered = renderer.renderImageWithDPI(i, dpi);
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                // JPEGFactory applies quality while embedding; this temporary image controls the raster resolution.
                ImageIO.write(rendered, "jpg", jpeg);
                PDPage sourcePage = input.getPage(i); PDRectangle box = sourcePage.getMediaBox();
                PDPage page = new PDPage(box); output.addPage(page);
                PDImageXObject image = JPEGFactory.createFromImage(output, ImageIO.read(new ByteArrayInputStream(jpeg.toByteArray())), quality);
                try (PDPageContentStream cs = new PDPageContentStream(output, page)) { cs.drawImage(image, 0, 0, box.getWidth(), box.getHeight()); }
                rendered.flush();
            }
            output.save(out); return out.toByteArray();
        }
    }

    @PostMapping("/edit")
    public ResponseEntity<byte[]> edit(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue="") String deletePages,
            @RequestParam(defaultValue="0") int rotateDegrees,
            @RequestParam(required=false) String pageOrder) throws IOException {
        validateFile(file);
        if (!Set.of(0,90,180,270).contains(rotateDegrees)) throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270 degrees.");
        try (PDDocument doc = PDDocument.load(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int count=doc.getNumberOfPages(); Set<Integer> del=parsePages(deletePages);
            for(int n:del) if(n>count) throw new IllegalArgumentException("Page "+n+" does not exist.");
            List<Integer> descending=new ArrayList<>(del); descending.sort(Comparator.reverseOrder());
            for(int n:descending) doc.removePage(n-1);
            if(doc.getNumberOfPages()==0) throw new IllegalArgumentException("You cannot delete every page.");
            if(rotateDegrees!=0) for(PDPage p:doc.getPages()) p.setRotation((p.getRotation()+rotateDegrees)%360);
            if(pageOrder!=null && !pageOrder.isBlank()) {
                List<Integer> order=new ArrayList<>(); for(String s:pageOrder.split(",")) { try { order.add(Integer.parseInt(s.trim())); } catch(Exception e) { throw new IllegalArgumentException("Page order must be comma-separated page numbers."); } }
                if(order.size()!=doc.getNumberOfPages() || new HashSet<>(order).size()!=order.size()) throw new IllegalArgumentException("Page order must list every remaining page exactly once.");
                PDDocument reordered=new PDDocument(); try { for(int n:order) { if(n<1||n>doc.getNumberOfPages()) throw new IllegalArgumentException("Page order contains an invalid page number."); reordered.importPage(doc.getPage(n-1)); } reordered.save(out); } finally { reordered.close(); }
            } else doc.save(out);
            return pdfResponse(out.toByteArray(), "edited.pdf");
        }
    }

    @PostMapping("/extract")
    public ResponseEntity<byte[]> extract(@RequestParam("file") MultipartFile file, @RequestParam("pages") String pages) throws IOException {
        validateFile(file); Set<Integer> selected=parsePages(pages);
        if(selected.isEmpty()) throw new IllegalArgumentException("Enter pages to extract, e.g. 1,3,5-7.");
        try(PDDocument in=PDDocument.load(file.getBytes()); PDDocument outDoc=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            for(int n:selected) { if(n>in.getNumberOfPages()) throw new IllegalArgumentException("Page "+n+" does not exist."); outDoc.importPage(in.getPage(n-1)); }
            outDoc.save(out); return pdfResponse(out.toByteArray(), "extracted-pages.pdf");
        }
    }

    @PostMapping("/split")
    public ResponseEntity<byte[]> split(@RequestParam("file") MultipartFile file) throws IOException {
        validateFile(file);
        try(PDDocument in=PDDocument.load(file.getBytes()); ByteArrayOutputStream zipBytes=new ByteArrayOutputStream(); ZipOutputStream zip=new ZipOutputStream(zipBytes)) {
            for(int i=0;i<in.getNumberOfPages();i++) { try(PDDocument one=new PDDocument(); ByteArrayOutputStream pdf=new ByteArrayOutputStream()) { one.importPage(in.getPage(i)); one.save(pdf); zip.putNextEntry(new ZipEntry(String.format("page-%03d.pdf",i+1))); zip.write(pdf.toByteArray()); zip.closeEntry(); } }
            zip.finish(); return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=split-pages.zip").body(zipBytes.toByteArray());
        }
    }

    @PostMapping("/watermark")
    public ResponseEntity<byte[]> watermark(@RequestParam("file") MultipartFile file, @RequestParam("text") String text,
            @RequestParam(defaultValue="45") float opacity, @RequestParam(defaultValue="45") float fontSize) throws IOException {
        validateFile(file); if(text==null||text.isBlank()||text.length()>120) throw new IllegalArgumentException("Enter watermark text (up to 120 characters).");
        try(PDDocument doc=PDDocument.load(file.getBytes()); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            for(PDPage p:doc.getPages()) try(PDPageContentStream cs=new PDPageContentStream(doc,p,PDPageContentStream.AppendMode.APPEND,true,true)) {
                cs.setNonStrokingColor(155,155,155); cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD,Math.max(10,Math.min(fontSize,100))); cs.newLineAtOffset(50,p.getMediaBox().getHeight()/2); cs.showText(text.replaceAll("[^\\x20-\\x7E]", "?")); cs.endText();
            }
            doc.save(out); return pdfResponse(out.toByteArray(),"watermarked.pdf");
        }
    }

    @PostMapping("/page-numbers")
    public ResponseEntity<byte[]> pageNumbers(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue="bottom-center") String position) throws IOException {
        validateFile(file);
        try(PDDocument doc=PDDocument.load(file.getBytes()); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            int i=0; for(PDPage p:doc.getPages()) { i++; PDRectangle b=p.getMediaBox(); float x=position.equals("bottom-right")?b.getWidth()-45:position.equals("bottom-left")?25:b.getWidth()/2-8; try(PDPageContentStream cs=new PDPageContentStream(doc,p,PDPageContentStream.AppendMode.APPEND,true,true)) { cs.beginText(); cs.setFont(PDType1Font.HELVETICA,10); cs.newLineAtOffset(x,20); cs.showText(String.valueOf(i)); cs.endText(); } }
            doc.save(out); return pdfResponse(out.toByteArray(),"numbered.pdf");
        }
    }

    @PostMapping("/protect")
    public ResponseEntity<byte[]> protect(@RequestParam("file") MultipartFile file, @RequestParam("password") String password) throws IOException {
        validateFile(file); if(password==null||password.length()<4||password.length()>100) throw new IllegalArgumentException("Password must be between 4 and 100 characters.");
        try(PDDocument doc=PDDocument.load(file.getBytes()); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            org.apache.pdfbox.pdmodel.encryption.AccessPermission ap=new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy policy=new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(password,password,ap); policy.setEncryptionKeyLength(128); policy.setPermissions(ap); doc.protect(policy); doc.save(out); return pdfResponse(out.toByteArray(),"protected.pdf");
        }
    }

    private Set<Integer> parsePages(String raw) {
        Set<Integer> result=new TreeSet<>(); if(raw==null||raw.isBlank()) return result;
        for(String part:raw.split(",")) { String s=part.trim(); try { if(s.contains("-")) { String[] p=s.split("-"); if(p.length!=2) throw new Exception(); int a=Integer.parseInt(p[0].trim()), b=Integer.parseInt(p[1].trim()); if(a<1||b<a||b-a>500) throw new IllegalArgumentException("Invalid page range: "+s); for(int i=a;i<=b;i++) result.add(i); } else { int n=Integer.parseInt(s); if(n<1) throw new IllegalArgumentException("Page numbers start at 1."); result.add(n); } } catch(NumberFormatException e) { throw new IllegalArgumentException("Invalid page number: "+s); } catch(IllegalArgumentException e) { throw e; } catch(Exception e) { throw new IllegalArgumentException("Invalid page range: "+s); } }
        return result;
    }
    private void validateFiles(List<MultipartFile> files,int min) { if(files==null||files.size()<min||files.size()>MAX_FILES) throw new IllegalArgumentException("Select between "+min+" and "+MAX_FILES+" PDF files."); for(MultipartFile f:files) validateFile(f); }
    private void validateFile(MultipartFile f) { if(f==null||f.isEmpty()) throw new IllegalArgumentException("Please select a PDF file."); if(f.getSize()>MAX_FILE_BYTES) throw new IllegalArgumentException("Each PDF must be 30 MB or smaller."); String n=f.getOriginalFilename(); if(n==null||!n.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new IllegalArgumentException("Only PDF files are accepted."); }
    private ResponseEntity<byte[]> pdfResponse(byte[] b,String name) { return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(b.length).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString()).cacheControl(CacheControl.noStore()).body(b); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(IOException.class) public ResponseEntity<Map<String,String>> pdfError(IOException e) { return ResponseEntity.badRequest().body(Map.of("error","Could not process this PDF. It may be corrupt, encrypted, or unsupported.")); }
}

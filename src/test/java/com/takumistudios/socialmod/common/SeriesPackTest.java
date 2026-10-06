package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SeriesPackTest {
    @TempDir Path root;
    private static final String A="config/fancymenu/customization/a.txt",B="config/fancymenu/customization/b.txt",ASSET="config/fancymenu/assets/snow.png";
    private void put(String path,String text) throws IOException { Path p=root.resolve(path);Files.createDirectories(p.getParent());Files.writeString(p,text); }
    private SeriesPack.Contents capture(String name,String... paths) throws IOException { return SeriesPack.capture(root,name,"Creator","1.0","26.3",List.of(paths)); }
    @Test void selectedExportExcludesUnrelatedSettingsAndLayouts() throws Exception {
        put(A,"source = [source:local]"+ASSET);put(ASSET,"image");put(B,"unrelated");put("config/fancymenu/options.txt","private preferences");
        var pack=capture("Winter",A);Path zip=root.resolve("winter.zip");SeriesPack.write(zip,pack);var read=SeriesPack.read(zip);
        assertEquals(Set.of(A,ASSET),read.files().keySet());assertEquals("Winter",read.metadata().name());assertTrue(read.metadata().requiredMods().contains("fancymenu"));
    }
    @Test void profileSwitchRestoresOriginalCollidingFilesAndPreservesOthers() throws Exception {
        put(A,"before");put(B,"other");put("config/fancymenu/customization/unrelated.txt","untouched");
        var first=capture("First",A);first.files().put(A,"first".getBytes());SeriesPack.install(root,first,"first.zip");
        var second=capture("Second",B);second.files().put(B,"second".getBytes());SeriesPack.install(root,second,"second.zip");
        assertEquals("before",Files.readString(root.resolve(A)));assertEquals("second",Files.readString(root.resolve(B)));assertEquals("second.zip",SeriesPack.active(root));
        assertEquals("untouched",Files.readString(root.resolve("config/fancymenu/customization/unrelated.txt")));
        SeriesPack.restore(root);assertEquals("first",Files.readString(root.resolve(A)));assertEquals("other",Files.readString(root.resolve(B)));assertEquals("first.zip",SeriesPack.active(root));
    }
    @Test void restoreRemovesNewFilesAndRecoversRowsByteForByte() throws Exception {
        put(SeriesPack.ROWS,RowDesign.GSON.toJson(RowDesign.defaults()));String original=Files.readString(root.resolve(SeriesPack.ROWS));
        var pack=capture("Winter");pack.files().put(A,"layout".getBytes());
        var m=pack.metadata();pack=new SeriesPack.Contents(new SeriesPack.Metadata(1,m.name(),m.author(),m.version(),m.minecraft(),List.of("socialmod","fabric-api","fancymenu","konkrete","melody")),pack.files());
        SeriesPack.install(root,pack,"winter.zip");SeriesPack.restore(root);
        assertFalse(Files.exists(root.resolve(A)));assertEquals(original,Files.readString(root.resolve(SeriesPack.ROWS)));assertEquals("",SeriesPack.active(root));
    }
    @Test void hudDeclaresSpiffyAndInvalidRowsNeverModifyInstance() throws Exception {
        put(A,"element_type = socialmod_hud_social");var pack=capture("HUD",A);assertTrue(pack.metadata().requiredMods().contains("spiffyhud"));
        pack.files().put(SeriesPack.ROWS,"{\"version\":9}".getBytes());assertThrows(IOException.class,()->SeriesPack.install(root,pack,"bad"));assertEquals("element_type = socialmod_hud_social",Files.readString(root.resolve(A)));
    }
    @Test void rejectsTraversalAbsoluteAndUnrelatedConfig() throws Exception {
        for(String path:List.of("../secret","/etc/passwd","C:/secret","config/socialmod/../secret","config/fancymenu/options.txt","config/socialmod/series/active.json")) {
            assertFalse(SeriesPack.allowed(path));Path zip=root.resolve("unsafe.zip");try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {out.putNextEntry(new ZipEntry(path));out.write(1);out.closeEntry();}
            assertThrows(IOException.class,()->SeriesPack.read(zip));
        }
    }
    @Test void rejectsMissingManifestMissingAssetsAndFalseDependencyDeclarations() throws Exception {
        put(A,"source = [source:local]"+ASSET);assertThrows(IOException.class,()->capture("Winter",A));put(ASSET,"image");var pack=capture("Winter",A);
        var bad=new SeriesPack.Contents(new SeriesPack.Metadata(1,"Bad","Me","1","26.3",List.of("socialmod","fabric-api")),pack.files());assertThrows(IOException.class,()->SeriesPack.validate(bad));
        Path zip=root.resolve("legacy.zip");try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {out.putNextEntry(new ZipEntry(A));out.write("layout".getBytes());out.closeEntry();}assertThrows(IOException.class,()->SeriesPack.read(zip));
    }
    @Test void limitsDecompressedEntryAndMetadata() throws Exception {
        Path zip=root.resolve("bomb.zip");try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {out.putNextEntry(new ZipEntry(ASSET));out.write(new byte[8*1024*1024+1]);out.closeEntry();}assertThrows(IOException.class,()->SeriesPack.read(zip));
        put(A,"layout");assertThrows(IOException.class,()->capture(" ",A));
    }
    @Test void retiredFilesCreatedByProfileAreRemoved() throws Exception {
        put(A,"first");var first=capture("First",A);Files.delete(root.resolve(A));SeriesPack.install(root,first,"first");
        put(B,"second");var second=capture("Second",B);SeriesPack.install(root,second,"second");assertFalse(Files.exists(root.resolve(A)));SeriesPack.restore(root);assertEquals("first",Files.readString(root.resolve(A)));
    }
    @Test void missingBackupFailsWithoutChanges() throws Exception { put(A,"unchanged");assertThrows(IOException.class,()->SeriesPack.restore(root));assertEquals("unchanged",Files.readString(root.resolve(A))); }
    @Test void writeFailureRollsBackAlreadyWrittenFilesAndKeepsForeignTemporaryDirectory() throws Exception {
        put(A,"original");put(B,"new");var pack=capture("Failed",A,B);pack.files().put(A,"changed".getBytes());Files.delete(root.resolve(B));
        Files.createDirectory(root.resolve(B+".tmp"));assertThrows(IOException.class,()->SeriesPack.install(root,pack,"failed"));
        assertEquals("original",Files.readString(root.resolve(A)));assertFalse(Files.exists(root.resolve(B)));assertTrue(Files.isDirectory(root.resolve(B+".tmp")));assertEquals("",SeriesPack.active(root));
    }
    @Test void rejectsSymlinkAncestors() throws Exception {
        Path outside=root.resolve("outside");Files.createDirectory(outside);Files.createDirectories(root.resolve("config/fancymenu"));
        try { Files.createSymbolicLink(root.resolve("config/fancymenu/assets"),outside); }
        catch(IOException|UnsupportedOperationException e) { org.junit.jupiter.api.Assumptions.assumeTrue(false,"Symlinks unavailable on this OS"); }
        assertThrows(IOException.class,()->SeriesPack.safe(root,ASSET));
    }
    @Test void documentedTemplatesHaveValidManifestsAndOnlySeriesFiles() throws Exception {
        Path repository=Path.of("").toAbsolutePath();while(repository!=null&&!Files.isDirectory(repository.resolve("docs/examples/christmas")))repository=repository.getParent();
        assertNotNull(repository,"Repository examples not found");
        for(String mc:List.of("26.1.2","26.2","26.3")) {
            var pack=SeriesPack.read(repository.resolve("docs/examples/christmas/christmas-modpack"+(mc.equals("26.3")?"":"-"+mc)+".zip"));
            assertEquals(mc,pack.metadata().minecraft());assertTrue(pack.metadata().requiredMods().contains("spiffyhud"));assertEquals(4,pack.files().size());
        }
    }
    @Test void retiredLayoutIsDisabledAndMissingLocalModelsReturnToBasic() throws Exception {
        put(A,"is_enabled = true\n");put(SeriesPack.ROWS,RowDesign.GSON.toJson(RowDesign.defaults()));
        var first=capture("First",A);SeriesPack.install(root,first,"first");
        put(B,"is_enabled = true\n");var second=capture("Second",B);second.files().remove(SeriesPack.ROWS);SeriesPack.install(root,second,"second");
        assertEquals("is_enabled = false",Files.readString(root.resolve(A)).strip());assertFalse(Files.exists(root.resolve(SeriesPack.ROWS)));
        SeriesPack.restore(root);assertTrue(Files.readString(root.resolve(A)).contains("is_enabled = true"));assertTrue(Files.exists(root.resolve(SeriesPack.ROWS)));
    }
}

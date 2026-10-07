package com.takumistudios.socialmod.common;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ChristmasArtworkTest {
    @Test void suppliedButtonPanelsKeepAlphaAndDecoratedAspect() throws Exception {
        for(String variant:new String[]{"red","green","wood","ice","gold","purple"})try(var stream=getClass().getResourceAsStream("/assets/socialmod/textures/christmas_graphic/buttons/"+variant+".png")) {
            assertNotNull(stream);var image=ImageIO.read(stream);assertNotNull(image);assertEquals(887,image.getWidth());assertTrue(image.getHeight()>=295&&image.getHeight()<=296);
            assertTrue(image.getColorModel().hasAlpha());assertEquals(0,image.getRGB(0,0)>>>24);assertTrue((image.getRGB(image.getWidth()/2,image.getHeight()/2)>>>24)>=250);
        }
    }
}

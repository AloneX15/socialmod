package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.model.RowDesign;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RowDesignTest {
    @Test void defaultsKeepExistingRowsUntilEnabled() { var d=RowDesign.defaults(); assertEquals(5,d.templates.size()); d.validate(); assertTrue(d.templates.values().stream().noneMatch(t->t.enabled)); }
    @Test void copyPreservesIndependentPartLayoutAndStates() { var d=RowDesign.defaults(); var t=d.templates.get("message"); t.enabled=true; t.parts.getFirst().x=17; t.selectedBackground=0x80ABCDEF; var copy=d.copy(); copy.templates.get("message").parts.getFirst().x=22; assertEquals(17,t.parts.getFirst().x); assertEquals(0x80ABCDEF,copy.templates.get("message").selectedBackground); }
    @Test void rejectsUnboundedLayouts() { var d=RowDesign.defaults(); d.templates.get("message").parts.getFirst().scale=0; assertThrows(IllegalArgumentException.class,d::validate); assertThrows(IllegalArgumentException.class,()->RowDesign.parse(" ".repeat(65537))); }
    @Test void rejectsUnknownContentAndVersion() { var d=RowDesign.defaults(); d.templates.get("player").parts.getFirst().field="execute_command"; assertThrows(IllegalArgumentException.class,d::validate); assertThrows(IllegalArgumentException.class,()->RowDesign.parse("{\"version\":2}")); }
    @Test void rejectsTraversalAndMalformedResources() { var d=RowDesign.defaults(); var p=d.templates.get("toast").parts.getFirst(); p.texture="socialmod:../secret"; assertThrows(IllegalArgumentException.class,d::validate); p.texture=""; p.font=null; assertThrows(IllegalArgumentException.class,d::validate); }
    @Test void acceptsAutomaticWidthWrappingAndRightAnchor() { var d=RowDesign.defaults(); var p=d.templates.get("message").parts.getLast(); p.width=0; p.wrap=true; p.right=true; assertTrue(d.copy().templates.get("message").parts.getLast().right); }
}

package net.lugo.overlaylib.util;


public record TextureSection (TextureSectionData textureSectionData, int xIndex, int yIndex) {
    public float uStart() {
        return xIndex * textureSectionData.uSize();
    }
    public float vStart() {
        return yIndex * textureSectionData.vSize();
    }
    public float uEnd() {
        return uStart() + textureSectionData.uSize();
    }
    public float vEnd() {
        return vStart() + textureSectionData.vSize();
    }
    public static final TextureSection SINGULAR = new TextureSection(TextureSectionData.SINGULAR, 0, 0);
    public record TextureSectionData (int xSections, int ySections) {
        public static final TextureSectionData SINGULAR = new TextureSectionData(1, 1);
        public float uSize() {
            return 1.0f / xSections;
        }
        public float vSize() {
            return 1.0f / ySections;
        }
    }
}
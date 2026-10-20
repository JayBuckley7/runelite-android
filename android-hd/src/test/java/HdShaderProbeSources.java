import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import rs117.hd.AndroidSupport;
import rs117.hd.opengl.shader.ShaderIncludes;
import rs117.hd.utils.ResourcePath;

/** Driver preflight using upstream include expansion and the same Android shader adaptation. */
public class HdShaderProbeSources {
    public static void main(String[] args) throws Exception {
        AndroidSupport.useFaceStorageBuffer = Boolean.parseBoolean(System.getProperty("hd.faceStorageBuffer", "false"));
        Path root=Path.of(args[0]), out=Path.of(args[1]); Files.createDirectories(out);
        Map<String,String> defs=new HashMap<>();
        try(var paths=Files.walk(root)) {
            for(Path p:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".glsl"))::iterator) {
                Matcher m=Pattern.compile("(?m)^\\s*#include ([A-Z_]+)\\s*$").matcher(Files.readString(p));
                while(m.find()) defs.put(m.group(1),"0");
            }
        }
        // Fixture counts exercise indexed UBO arrays; actual game counts are verified during live startup.
        defs.put("NEBULA_CLUSTER_COUNT",Integer.toString(rs117.hd.scene.daylight_cycle.StarField.CLUSTER_COUNT)); defs.put("SHADOW_FILTERING_KERNEL","3");
        defs.put("ZONE_RENDERER","1"); defs.put("MATERIAL_COUNT","512"); defs.put("WATER_TYPE_COUNT","32");
        defs.put("MAX_SIMULTANEOUS_WORLD_VIEWS","64"); defs.put("MAX_LIGHT_COUNT","16");
        defs.put("TILED_LIGHTING_LAYER_COUNT","1"); defs.put("TILED_LIGHTING_TILE_SIZE","16");
        defs.put("SHADOW_RESOLUTION","1024"); defs.put("NORMAL_MAPPING","1");
        defs.put("UNDO_VANILLA_SHADING","1"); defs.put("WIND_DISPLACEMENT_NOISE_RESOLUTION","0.04");
        StringBuilder materials=new StringBuilder(); Set<String> names=new TreeSet<>();
        try(var paths=Files.walk(root)) {
            for(Path p:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".glsl"))::iterator) {
                Matcher m=Pattern.compile("\\bMAT_[A-Z_0-9]+\\b").matcher(Files.readString(p));
                while(m.find()) names.add(m.group());
            }
        }
        int mi=0; for(String name:names) materials.append("#define ").append(name).append(" getMaterial(").append(mi++).append(")\n");
        for(String mode:List.of("basic","lit","shadow","mobile","tiled","effects","pcss","dither","average")) {
            for(String name:List.of("scene_vert.glsl","scene_frag.glsl","ui_vert.glsl","ui_frag.glsl","shadow_vert.glsl","shadow_frag.glsl",
                "overlays/overlay_vert.glsl", "overlays/gamma_calibration_frag.glsl",
                "sky_vert.glsl", "sky_frag.glsl", "star_vert.glsl", "star_frag.glsl",
                "sky_nebula_bake_vert.glsl", "sky_nebula_bake_frag.glsl",
                "water_vert.glsl", "water_frag.glsl", "terrain_vert.glsl", "terrain_frag.glsl",
                "tiled_vert.glsl", "tiled_frag.glsl", "gap_vert.glsl", "gap_frag.glsl")) {
                String sourceName=name.replace("water_", "scene_").replace("terrain_", "shadow_").replace("tiled_", "tiled_lighting_");
                if(name.equals("terrain_frag.glsl")) sourceName="stub_frag.glsl";
                if(name.equals("gap_vert.glsl")) sourceName="scene_vert.glsl";
                if(name.equals("gap_frag.glsl")) sourceName="gap_filler_frag.glsl";
                boolean shadows=List.of("shadow","pcss","dither","average").contains(mode);
                ShaderIncludes inc=new ShaderIncludes().addIncludePath(ResourcePath.path(root.toString()));
                defs.forEach(inc::define);
                inc.define("SHADOW_MODE",shadows?2:0)
                   .define("DAYLIGHT_CYCLE", !mode.equals("basic") && !mode.equals("mobile"))
                   .define("NEBULAE", mode.equals("effects"))
                   .define("STAR_MODE", mode.equals("effects") ? 2 : 1)
                   .define("POINT_SPRITES", !mode.equals("effects"))
                   .define("TERRAIN_SHADOWS", shadows)
                   .define("TERRAIN_ONLY_PASS", name.startsWith("terrain_"))
                   .define("WATER_PASS", name.startsWith("water_"))
                   .define("WATER_SPECULAR_REFLECTIONS", true)
                   .define("DYNAMIC_LIGHTS",!mode.equals("basic"))
                   .define("PARALLAX_OCCLUSION_MAPPING",mode.equals("shadow"))
                   .define("SHADOW_TRANSPARENCY",shadows)
                   .define("SHADOW_FILTERING", mode.equals("pcss")?3:mode.equals("dither")?1:mode.equals("average")?2:0)
                   .define("SHADOW_FILTERING_KERNEL",mode.equals("shadow")?3:2)
                   .define("UI_SCALING_MODE", List.of("basic","lit","shadow","mobile","tiled","effects","pcss","dither","average").indexOf(mode) % 6)
                   .define("WIND_DISPLACEMENT", mode.equals("mobile") || mode.equals("effects"))
                   .define("CHARACTER_DISPLACEMENT", mode.equals("effects"))
                   .define("MAX_CHARACTER_POSITION_COUNT", 16)
                   .define("TILED_LIGHTING", mode.equals("tiled"))
                   .define("FLAT_SHADING", mode.equals("effects"))
                   .define("APPLY_COLOR_FILTER", mode.equals("effects"))
                   .define("COLOR_BLINDNESS", mode.equals("effects") ? 1 : 0)
                   .define("WIREFRAME", mode.equals("effects"))
                   .define("CENTROID_QUALIFIER", mode.equals("effects"))
                   .define("REDUCE_GLARE_INTENSITY", mode.equals("effects"))
                   .define("VANILLA_COLOR_BANDING", mode.equals("effects"))
                   .addInclude("MATERIAL_GETTER","#define getMaterial(i) uboMaterials.Array[i]")
                   .addInclude("WATER_TYPE_GETTER","#define getWaterType(i) uboWaterTypes.Array[i]")
                   .addInclude("WORLD_VIEW_GETTER","#define getWorldView(i) uboWorldViews.Array[i]")
                   .addInclude("MATERIAL_CONSTANTS",materials.toString())
                   .addInclude("SHADER_TYPE","#define VERTEX_SHADER 35633\n#define FRAGMENT_SHADER 35632\n#define GEOMETRY_SHADER 36313\n#define COMPUTE_SHADER 37305\n#define SHADER_TYPE "+(name.contains("vert")?35633:35632));
                String outputName=name.replace("overlays/overlay", "gamma").replace("overlays/gamma_calibration", "gamma");
                Path file=out.resolve(mode+"_"+outputName);
                if(mode.equals("mobile")) inc.define("UI_SCALING_MODE", 5).define("PARALLAX_OCCLUSION_MAPPING", true).define("DYNAMIC_LIGHTS", false);
                if(name.startsWith("terrain_")) inc.define("SHADOW_MODE", 1).define("SHADOW_TRANSPARENCY", false);
                if(name.startsWith("tiled_")) inc.define("DYNAMIC_LIGHTS", true);
                Files.writeString(file,AndroidSupport.adaptShader(inc.loadFile(sourceName)));
                var sources=ShaderIncludes.class.getDeclaredField("includeList"); sources.setAccessible(true);
                @SuppressWarnings("unchecked") var paths=(List<String>)sources.get(inc);
                Files.write(out.resolve(mode+"_"+outputName+".sources"),paths);
                System.out.println(file.getFileName());
            }
        }
    }
}

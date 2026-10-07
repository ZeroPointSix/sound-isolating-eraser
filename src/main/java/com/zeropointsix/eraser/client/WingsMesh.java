package com.zeropointsix.eraser.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.zeropointsix.eraser.ModMain;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

public final class WingsMesh implements ResourceManagerReloadListener {
    public static final WingsMesh INSTANCE = new WingsMesh();
    public static final ResourceLocation MODEL = new ResourceLocation(ModMain.MOD_ID, "models/wind_thunder_wings.mesh.json");
    public static final ResourceLocation TEXTURE = new ResourceLocation(ModMain.MOD_ID, "textures/entity/wings_mesh_white.png");
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile MeshData data = MeshData.EMPTY;

    private WingsMesh() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        try (Reader reader = manager.openAsReader(MODEL)) {
            MeshData loaded = parse(JsonParser.parseReader(reader).getAsJsonObject());
            data = loaded;
            LOGGER.info("Loaded Wind Thunder mesh: {} parts, {} triangles", loaded.parts.size(), loaded.triangles);
        } catch (Exception error) {
            data = MeshData.EMPTY;
            LOGGER.error("Cannot load {}. Wings will not be replaced with flat placeholder planes.", MODEL, error);
        }
    }

    public boolean isLoaded() {
        return !data.parts.isEmpty();
    }

    public int triangleCount() {
        return data.triangles;
    }

    public float[] mountPivot() {
        return data.mount.clone();
    }

    public void render(PoseStack stack, VertexConsumer vertices, int light, int overlay,
            float deployment, float phase, float amplitude, float sweep, float opacity) {
        MeshData mesh = data;
        if (mesh.parts.isEmpty()) {
            return;
        }
        float scale = Mth.lerp(Mth.clamp(deployment, 0, 1), mesh.miniatureScale, 1.0F);
        stack.pushPose();
        stack.translate(mesh.mount[0], mesh.mount[1], mesh.mount[2]);
        stack.scale(scale, scale, scale);
        stack.translate(-mesh.mount[0], -mesh.mount[1], -mesh.mount[2]);
        for (Part part : mesh.parts) {
            // Runtime particles already own thunder/blink FX. Do not render the studio burst meshes.
            if (!part.channel.equals("body")) {
                continue;
            }
            stack.pushPose();
            for (Bone bone : part.chain) {
                if (bone.kind.isEmpty()) {
                    continue;
                }
                stack.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
                switch (bone.kind) {
                    case "main" -> {
                        stack.mulPose(Axis.ZP.rotationDegrees(-part.side * amplitude * Mth.sin(phase) * 4));
                        stack.mulPose(Axis.YP.rotationDegrees(-part.side * sweep * 60));
                    }
                    case "outer" -> stack.mulPose(Axis.ZP.rotationDegrees(
                            part.side * amplitude * Mth.sin(phase - 0.3f) * 1.4f));
                    case "fan" -> stack.mulPose(Axis.ZP.rotationDegrees(
                            -part.side * amplitude * Mth.sin(phase - 0.5f) * 2));
                    case "primary", "secondary", "covert" -> {
                        float lag = 0.55f + bone.order * 0.11f;
                        float flex = bone.kind.equals("primary") ? 1.5f : 0.6f;
                        // Feather fan settles last during the short deployment, then follows the wrist.
                        float settle = Mth.sin((float) Math.PI * deployment) * 2;
                        stack.mulPose(Axis.ZP.rotationDegrees(-part.side
                                * (amplitude * Mth.sin(phase - lag) * flex + settle)));
                    }
                    default -> {
                    }
                }
                stack.translate(-bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);
            }
            PoseStack.Pose pose = stack.last();
            for (int index = 0; index < part.faces.length; index++) {
                int[] face = part.faces[index];
                float[] normal = part.normals[index];
                Material material = mesh.materials.get(part.materials[index]);
                int packedLight = material.emissive ? LightTexture.FULL_BRIGHT : light;
                int alpha = Mth.clamp(Math.round(material.alpha * opacity * 255), 0, 255);
                // Entity render types consume QUADS: the fourth corner makes a
                // degenerate second triangle without changing the source mesh.
                emit(vertices, pose, part.points[face[0]], normal, material, alpha, packedLight, overlay, false);
                emit(vertices, pose, part.points[face[1]], normal, material, alpha, packedLight, overlay, false);
                emit(vertices, pose, part.points[face[2]], normal, material, alpha, packedLight, overlay, false);
                emit(vertices, pose, part.points[face[2]], normal, material, alpha, packedLight, overlay, false);
            }
            stack.popPose();
        }
        stack.popPose();
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, float[] point, float[] normal,
            Material color, int alpha, int light, int overlay, boolean indigo) {
        consumer.vertex(pose.pose(), point[0], point[1], point[2])
                .color(indigo ? color.indigoRed : color.red, indigo ? color.indigoGreen : color.green,
                        indigo ? color.indigoBlue : color.blue, alpha).uv(0.5F, 0.5F)
                .overlayCoords(overlay).uv2(light)
                .normal(pose.normal(), normal[0], normal[1], normal[2]).endVertex();
    }

    private static MeshData parse(JsonObject root) {
        if (root.get("schema_version").getAsInt() != 1
                || !root.get("schema").getAsString().equals("fenglei.rigid_triangle_mesh")) {
            throw new IllegalArgumentException("Unsupported wings mesh schema");
        }
        JsonObject coordinates = root.getAsJsonObject("coordinates");
        if (!coordinates.get("unit").getAsString().equals("minecraft_block")
                || !coordinates.get("y").getAsString().equals("up")) {
            throw new IllegalArgumentException("Mesh must use blocks and an upward Y axis");
        }
        List<Material> materials = new ArrayList<>();
        for (JsonElement entry : root.getAsJsonArray("materials")) {
            JsonObject value = entry.getAsJsonObject();
            int[] rgb = integers(value.getAsJsonArray("rgb"), 3);
            if (value.get("id").getAsInt() != materials.size()) {
                throw new IllegalArgumentException("Material IDs must be ordered");
            }
            for (int component : rgb) {
                if (component < 0 || component > 255) {
                    throw new IllegalArgumentException("Invalid material color");
                }
            }
            float strength = value.get("emission_strength").getAsFloat();
            String name = value.get("name").getAsString();
            int[] alternate = rgb;
            if (name.startsWith("Feather")) {
                alternate = name.contains("shadow") ? new int[] {0x18, 0x38, 0x4D}
                        : name.contains("highlight") ? new int[] {0x9D, 0xD9, 0xDE} : new int[] {0x3C, 0x79, 0x91};
            } else if (name.contains("gold inlay")) {
                alternate = new int[] {0xBC, 0x96, 0x57};
            }
            materials.add(new Material(rgb[0], rgb[1], rgb[2], alternate[0], alternate[1], alternate[2],
                    finite(value.get("alpha")) * (name.startsWith("Feather") ? 0.88f : 1f), strength > 0.5F));
        }
        Map<String, Bone> bones = new HashMap<>();
        for (JsonElement entry : root.getAsJsonArray("bones")) {
            JsonObject value = entry.getAsJsonObject();
            String name = value.get("name").getAsString();
            String parent = value.get("parent").isJsonNull() ? null : value.get("parent").getAsString();
            String kind = name.endsWith("_main") ? "main" : name.endsWith("_outer") ? "outer"
                    : name.endsWith("_fan") ? "fan" : name.contains("_primary_") ? "primary"
                    : name.contains("_secondary_") ? "secondary" : name.contains("_covert_") ? "covert" : "";
            int order = kind.equals("primary") || kind.equals("secondary") || kind.equals("covert")
                    ? Integer.parseInt(name.substring(name.lastIndexOf('_') + 1)) : 0;
            bones.put(name, new Bone(name, parent, vector(value.getAsJsonArray("pivot")), kind, order));
        }
        List<Part> parts = new ArrayList<>();
        int triangleCount = 0;
        int vertexCount = 0;
        JsonArray meshEntries = root.getAsJsonArray("meshes");
        if (meshEntries.size() > 2048) {
            throw new IllegalArgumentException("Too many mesh parts");
        }
        for (JsonElement entry : meshEntries) {
            JsonObject value = entry.getAsJsonObject();
            float[][] points = vectors(value.getAsJsonArray("vertices"));
            float[][] normals = vectors(value.getAsJsonArray("normals"));
            JsonArray triangleArray = value.getAsJsonArray("triangles");
            int[][] faces = new int[triangleArray.size()][];
            int[] materialIds = integers(value.getAsJsonArray("material_ids"), faces.length);
            if (faces.length != normals.length) {
                throw new IllegalArgumentException("Triangle and normal counts differ");
            }
            for (int i = 0; i < faces.length; i++) {
                faces[i] = integers(triangleArray.get(i).getAsJsonArray(), 3);
                for (int vertex : faces[i]) {
                    if (vertex < 0 || vertex >= points.length) {
                        throw new IllegalArgumentException("Triangle vertex outside mesh");
                    }
                }
                float[] normal = normals[i];
                float length = normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2];
                if (Math.abs(length - 1) > 0.001F || materialIds[i] < 0 || materialIds[i] >= materials.size()) {
                    throw new IllegalArgumentException("Invalid normal or material index");
                }
            }
            List<Bone> chain = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            String next = value.get("bone").getAsString();
            while (next != null) {
                Bone bone = bones.get(next);
                if (bone == null || !visited.add(next) || visited.size() > 64) {
                    throw new IllegalArgumentException("Missing or cyclic bone binding");
                }
                chain.add(0, bone);
                next = bone.parent;
            }
            String side = value.get("side").getAsString();
            int sign = switch (side) {
                case "left" -> -1;
                case "right" -> 1;
                case "center" -> 0;
                default -> throw new IllegalArgumentException("Unknown wing side");
            };
            parts.add(new Part(points, faces, normals, materialIds, List.copyOf(chain), sign,
                    value.get("visibility_channel").getAsString()));
            triangleCount += faces.length;
            vertexCount += points.length;
            if (vertexCount > 250000 || triangleCount > 500000) {
                throw new IllegalArgumentException("Mesh exceeds renderer budget");
            }
        }
        JsonObject mount = root.getAsJsonObject("mount");
        float miniatureScale = finite(mount.get("miniature_scale"));
        if (miniatureScale <= 0 || miniatureScale > 1) {
            throw new IllegalArgumentException("Invalid miniature scale");
        }
        return new MeshData(List.copyOf(parts), List.copyOf(materials), vector(mount.getAsJsonArray("pivot")), miniatureScale, triangleCount);
    }

    private static float finite(JsonElement value) {
        float result = value.getAsFloat();
        if (!Float.isFinite(result)) {
            throw new IllegalArgumentException("Non-finite mesh coordinate");
        }
        return result;
    }

    private static float[] vector(JsonArray values) {
        if (values.size() != 3) {
            throw new IllegalArgumentException("Expected three vector components");
        }
        return new float[] {finite(values.get(0)), finite(values.get(1)), finite(values.get(2))};
    }

    private static float[][] vectors(JsonArray values) {
        if (values.size() > 500000) {
            throw new IllegalArgumentException("Mesh array too large");
        }
        float[][] result = new float[values.size()][];
        for (int i = 0; i < result.length; i++) {
            result[i] = vector(values.get(i).getAsJsonArray());
        }
        return result;
    }

    private static int[] integers(JsonArray values, int length) {
        if (values.size() != length) {
            throw new IllegalArgumentException("Mesh attribute count differs");
        }
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = values.get(i).getAsInt();
        }
        return result;
    }

    private record Material(int red, int green, int blue, int indigoRed, int indigoGreen, int indigoBlue, float alpha, boolean emissive) {
    }

    private record Bone(String name, String parent, float[] pivot, String kind, int order) {
    }

    private record Part(float[][] points, int[][] faces, float[][] normals, int[] materials,
            List<Bone> chain, int side, String channel) {
    }

    private record MeshData(List<Part> parts, List<Material> materials, float[] mount, float miniatureScale, int triangles) {
        private static final MeshData EMPTY = new MeshData(List.of(), List.of(), new float[] {0, 1.48F, 0.29F}, 0.0625F, 0);
    }
}

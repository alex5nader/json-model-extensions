/*******************************************************************************
 * Copyright 2019 grondag
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package grondag.jmx.json.model;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.ObjectUtils;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BakedQuadFactory;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelElement;
import net.minecraft.client.render.model.json.ModelElementFace;
import net.minecraft.client.render.model.json.ModelElementTexture;
import net.minecraft.client.render.model.json.ModelItemPropertyOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;
import net.fabricmc.fabric.api.util.TriState;

import grondag.jmx.impl.TransformableModel;
import grondag.jmx.impl.TransformableModelContext;
import grondag.jmx.json.ext.FaceExtData;
import grondag.jmx.json.ext.JmxExtension;
import grondag.jmx.json.ext.JmxMaterial;
import grondag.jmx.json.ext.JmxModelExt;
import grondag.jmx.target.FrexHolder;

@Environment(EnvType.CLIENT)
public class JmxBakedModel implements BakedModel, FabricBakedModel, TransformableModel {
	protected static final Renderer RENDERER = RendererAccess.INSTANCE.getRenderer();
	protected static final boolean FREX_RENDERER = FrexHolder.target().isFrexRendererAvailable();

	protected final Mesh mesh;
	protected WeakReference<List<BakedQuad>[]> quadLists = null;
	protected final boolean usesAo;
	protected final boolean isSideLit;
	protected final Sprite particleSprite;
	protected final ModelTransformation transformation;
	protected final ModelItemPropertyOverrideList itemPropertyOverrides;
	protected final boolean hasDepth;

	public JmxBakedModel(Mesh mesh, boolean usesAo, boolean isSideLit, Sprite particleSprite, ModelTransformation transformation, ModelItemPropertyOverrideList itemPropertyOverrides, boolean hasDepth) {
		this.mesh = mesh;
		this.usesAo = usesAo;
		this.isSideLit = isSideLit;
		this.particleSprite = particleSprite;
		this.transformation = transformation;
		this.itemPropertyOverrides = itemPropertyOverrides;
		this.hasDepth = hasDepth;
	}

	@SuppressWarnings("resource")
	@Override
	public BakedModel derive(TransformableModelContext context) {
		final SpriteAtlasTexture atlas = MinecraftClient.getInstance().getBakedModelManager().method_24153(SpriteAtlasTexture.BLOCK_ATLAS_TEX);
		final MeshBuilder meshBuilder = RendererAccess.INSTANCE.getRenderer().meshBuilder();
		final QuadEmitter emitter = meshBuilder.getEmitter();
		final Sprite newParticleSprite = context.spriteTransform().mapSprite(particleSprite, atlas);
		final QuadTransform transform = context.quadTransform();

		mesh.forEach(q -> {
			emitter.material(q.material());
			q.copyTo(emitter);
			if(transform.transform(emitter)) {
				emitter.emit();
			}
		});

		return new JmxBakedModel(meshBuilder.build(), usesAo, isSideLit, newParticleSprite, transformation,
				transformItemProperties(context, atlas, meshBuilder), hasDepth);
	}

	private ModelItemPropertyOverrideList transformItemProperties(TransformableModelContext context, SpriteAtlasTexture atlas, MeshBuilder meshBuilder) {
		//TODO: Implement
		return itemPropertyOverrides;
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction face, Random rand) {
		List<BakedQuad>[] lists = quadLists == null ? null : quadLists.get();
		if(lists == null) {
			lists = ModelHelper.toQuadLists(mesh);
			quadLists = new WeakReference<>(lists);
		}
		final List<BakedQuad> result = lists[face == null ? 6 : face.getId()];
		return result == null ? ImmutableList.of() : result;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return usesAo;
	}

	@Override
	public boolean hasDepth() {
		return hasDepth;
	}

	@Override
	public boolean isSideLit() {
		return isSideLit;
	}

	@Override
	public boolean isBuiltin() {
		return false;
	}

	@Override
	public Sprite getSprite() {
		return particleSprite;
	}

	@Override
	public ModelTransformation getTransformation() {
		return transformation;
	}

	@Override
	public ModelItemPropertyOverrideList getItemPropertyOverrides() {
		return itemPropertyOverrides;
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
		if(mesh != null) {
			context.meshConsumer().accept(mesh);
		}
	}

	@Override
	public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
		if(mesh != null) {
			context.meshConsumer().accept(mesh);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final MeshBuilder meshBuilder;
		private final MaterialFinder finder;
		private final QuadEmitter emitter;
		private final ModelItemPropertyOverrideList itemPropertyOverrides;
		private final boolean usesAo;
		private Sprite particleTexture;
		private final boolean isSideLit;
		private final ModelTransformation transformation;
		private final boolean hasDepth;

		public Builder(JsonUnbakedModel unbakedModel, ModelItemPropertyOverrideList itemPropertyOverrides, boolean hasDepth) {
			this(unbakedModel.useAmbientOcclusion(), unbakedModel.getGuiLight().isSide(), unbakedModel.getTransformations(), itemPropertyOverrides, hasDepth);
		}

		private Builder(boolean usesAo, boolean isSideLit, ModelTransformation transformation, ModelItemPropertyOverrideList itemPropertyOverrides, boolean hasDepth) {
			meshBuilder = RENDERER.meshBuilder();
			finder = RENDERER.materialFinder();
			emitter = meshBuilder.getEmitter();
			this.itemPropertyOverrides = itemPropertyOverrides;
			this.usesAo = usesAo;
			this.isSideLit = isSideLit;
			this.transformation = transformation;
			this.hasDepth = hasDepth;
		}

		public JmxBakedModel.Builder setParticle(Sprite sprite) {
			particleTexture = sprite;
			return this;
		}

		public BakedModel build() {
			if (particleTexture == null) {
				throw new RuntimeException("Missing particle!");
			} else {
				return new JmxBakedModel(meshBuilder.build(), usesAo, isSideLit, particleTexture, transformation, itemPropertyOverrides, hasDepth);
			}
		}

		private static final BakedQuadFactory QUADFACTORY = new BakedQuadFactory();
		private static final BakedQuadFactoryExt QUADFACTORY_EXT = (BakedQuadFactoryExt)QUADFACTORY;

		/**
		 * Intent here is to duplicate vanilla baking exactly.  Code is adapted from BakedQuadFactory.
		 */
		public void addQuad(Direction cullFace, JmxModelExt modelExt, Function<String, Sprite> spriteFunc, ModelElement element, ModelElementFace elementFace, Sprite sprite, Direction face, ModelBakeSettings bakeProps, Identifier modelId) {
			@SuppressWarnings("unchecked")
			final
			FaceExtData extData = ObjectUtils.defaultIfNull(((JmxExtension<FaceExtData>)elementFace).jmx_ext(), FaceExtData.EMPTY);
			final JmxMaterial jmxMat = modelExt == null ? JmxMaterial.DEFAULT : modelExt.resolveMaterial(extData.jmx_material);

			final RenderMaterial mat = getPrimaryMaterial(jmxMat, element);

			final QuadEmitter emitter = this.emitter;
			emitter.material(mat);
			emitter.cullFace(cullFace);

			if(jmxMat.tag != 0) {
				emitter.tag(jmxMat.tag);
			}

			ModelElementTexture tex = extData.jmx_texData0 == null ? elementFace.textureData : extData.jmx_texData0;

			QUADFACTORY_EXT.bake(emitter, 0, element, elementFace, tex, sprite, face, bakeProps, modelId);
			final int color0 = jmxMat.color0;

			emitter.spriteColor(0, color0, color0, color0, color0);
			emitter.colorIndex(elementFace.tintIndex);

			if(FREX_RENDERER) {
				if(jmxMat.depth == 2) {
					tex = extData.jmx_texData1 == null ? elementFace.textureData : extData.jmx_texData1;
					sprite = spriteFunc.apply(extData.jmx_tex1);
					QUADFACTORY_EXT.bake(emitter, 1, element, elementFace, tex, sprite, face, bakeProps, modelId);
					final int color1 = jmxMat.color1;
					emitter.spriteColor(1, color1, color1, color1, color1);
				}

				// With FREX will emit both sprites as one quad
				emitter.emit();
			} else {
				emitter.emit();

				if(jmxMat.depth == 2) {
					tex = extData.jmx_texData1 == null ? elementFace.textureData : extData.jmx_texData1;

					if(jmxMat.tag != 0) {
						emitter.tag(jmxMat.tag);
					}

					emitter.material(getSecondaryMaterial(jmxMat, element));
					emitter.cullFace(cullFace);
					sprite = spriteFunc.apply(extData.jmx_tex1);
					QUADFACTORY_EXT.bake(emitter, 0, element, elementFace, tex, sprite, face, bakeProps, modelId);
					final int color1 = jmxMat.color1;
					emitter.spriteColor(0, color1, color1, color1, color1);
					emitter.colorIndex(elementFace.tintIndex);
					emitter.emit();
				}
			}
		}

		private RenderMaterial getPrimaryMaterial(JmxMaterial jmxMat, ModelElement element) {
			if(FREX_RENDERER && jmxMat.preset != null) {
				RenderMaterial mat = null;
				mat = FrexHolder.target().loadFrexMaterial(new Identifier(jmxMat.preset));

				if(mat != null) {
					return mat;
				}
			}

			final MaterialFinder finder = this.finder.clear();
			finder.disableDiffuse(0, (hasDepth && !FREX_RENDERER) || (jmxMat.diffuse0 == TriState.DEFAULT ? !element.shade : !jmxMat.diffuse0.get()));
			finder.disableAo(0, jmxMat.ao0 == TriState.DEFAULT ? !usesAo : !jmxMat.ao0.get());
			finder.emissive(0, jmxMat.emissive0.get());

			if(jmxMat.colorIndex0 == TriState.FALSE) {
				finder.disableColorIndex(0, true);
			}

			if(jmxMat.layer0 != null) {
				finder.blendMode(0, jmxMat.layer0);
			}

			if(FREX_RENDERER && jmxMat.depth == 2) {
				finder.spriteDepth(2);
				finder.disableDiffuse(1, jmxMat.diffuse1 == TriState.DEFAULT ? !element.shade : !jmxMat.diffuse1.get());
				finder.disableAo(1, jmxMat.ao1 == TriState.DEFAULT ? !usesAo : !jmxMat.ao1.get());
				finder.emissive(1, jmxMat.emissive1.get());

				if(jmxMat.colorIndex1 == TriState.FALSE) {
					finder.disableColorIndex(1, true);
				}

				if(jmxMat.layer1 != null) {
					finder.blendMode(1, jmxMat.layer1);
				}
			}

			return finder.find();
		}

		/**
		 * Material used for 2nd layer when FREX renderer not available.
		 */
		private RenderMaterial getSecondaryMaterial(JmxMaterial jmxMat, ModelElement element) {
			final MaterialFinder finder = this.finder.clear();
			finder.disableDiffuse(0, (hasDepth && !FREX_RENDERER) || (jmxMat.diffuse1 == TriState.DEFAULT ? !element.shade : !jmxMat.diffuse1.get()));
			finder.disableAo(0, hasDepth || (jmxMat.ao1 == TriState.DEFAULT ? !usesAo : !jmxMat.ao1.get()));
			finder.emissive(0, jmxMat.emissive1.get());

			if(jmxMat.colorIndex1 == TriState.FALSE) {
				finder.disableColorIndex(0, true);
			}

			if(jmxMat.layer1 != null) {
				finder.blendMode(0, jmxMat.layer1);
			}

			return finder.find();
		}
	}
}


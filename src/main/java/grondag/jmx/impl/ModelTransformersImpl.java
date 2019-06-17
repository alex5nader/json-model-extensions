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

package grondag.jmx.impl;

import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import grondag.jmx.api.ModelTransformer;
import grondag.jmx.api.ModelTransformerRegistry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.model.ModelProviderContext;
import net.fabricmc.fabric.api.client.model.ModelProviderException;
import net.fabricmc.fabric.api.client.model.ModelVariantProvider;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.resource.ResourceManager;

public class ModelTransformersImpl implements ModelTransformerRegistry, ModelVariantProvider, Function<ResourceManager, ModelVariantProvider> {
    private ModelTransformersImpl() {}
    
    public static final ModelTransformersImpl INSTANCE = new ModelTransformersImpl();
    
    private final Object2ObjectOpenHashMap<String, Pair<String, ModelTransformer>> blockTargets = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, Pair<String, ModelTransformer>> itemTargets = new Object2ObjectOpenHashMap<>();
    private boolean isEmpty = true;
    
    public boolean isEmpty() {
        return isEmpty;
    }
    
    @Override
    public void addBlock(String targetModel, String sourceModel, ModelTransformer transform) {
        isEmpty = false;
        blockTargets.put(targetModel, Pair.of(sourceModel, transform));
    }
    
    @Override
    public void addItem(String targetModel, String sourceModel, ModelTransformer transform) {
        isEmpty = false;
        itemTargets.put(targetModel, Pair.of(sourceModel, transform));
    }
    
    @Override
    public void add(String targetModel, String sourceModel, ModelTransformer transform) {
        addBlock(targetModel, sourceModel, transform);
        addItem(targetModel, sourceModel, transform);
    }
    
    @Override
    public UnbakedModel loadModelVariant(ModelIdentifier modelId, ModelProviderContext context) throws ModelProviderException {
        final String fromString = modelId.getNamespace() + ":" + modelId.getPath();
        final Pair<String, ModelTransformer> match  = modelId.getVariant().equals("inventory")
                ? itemTargets.get(fromString) :  blockTargets.get(fromString);
                
        if(match != null) {
            ModelIdentifier templateId = new ModelIdentifier(match.getLeft(), modelId.getVariant());
            return new LazyModelDelegate(templateId, match.getRight());
        }
        return null;
    }

    @Override
    public ModelVariantProvider apply(ResourceManager resourceManager) {
        return this;
    }
}
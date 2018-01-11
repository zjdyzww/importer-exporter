package org.citydb.citygml.exporter.util;

import java.util.HashSet;
import java.util.Iterator;

import org.citydb.citygml.common.database.xlink.DBXlink;
import org.citydb.concurrent.WorkerPool;
import org.citydb.config.internal.Internal;
import org.citygml4j.model.citygml.appearance.AbstractSurfaceData;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.appearance.AppearanceProperty;
import org.citygml4j.model.citygml.appearance.GeoreferencedTexture;
import org.citygml4j.model.citygml.appearance.ParameterizedTexture;
import org.citygml4j.model.citygml.appearance.SurfaceDataProperty;
import org.citygml4j.model.citygml.appearance.TextureAssociation;
import org.citygml4j.model.citygml.appearance.X3DMaterial;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.gml.geometry.aggregates.MultiSurface;
import org.citygml4j.model.gml.geometry.primitives.AbstractSurface;
import org.citygml4j.util.walker.FeatureWalker;
import org.citygml4j.util.walker.GMLWalker;

public class AppearanceRemover {
	private final WorkerPool<DBXlink> xlinkPool;
	private final TargetWalker collector;
	private final RemoverWalker remover;

	public AppearanceRemover(WorkerPool<DBXlink> xlinkPool) {
		this.xlinkPool = xlinkPool;
		remover = new RemoverWalker();
		collector = new TargetWalker();
	}
	
	private enum RemoverState {
		REMOVE_TARGETS,
		REMOVE_XLINKS
	}

	public void cleanupAppearance(AbstractCityObject cityObject) {
		// step 1: collect possible surface data targets
		collector.targets = new HashSet<>();
		cityObject.accept(collector);

		// step 2: remove dead targets from surface data
		remover.state = RemoverState.REMOVE_TARGETS;
		remover.targets = collector.targets;
		remover.xlinks = new HashSet<>();
		cityObject.accept(remover);

		// step 3: remove xlinks to removed surface data objects
		remover.state = RemoverState.REMOVE_XLINKS;
		cityObject.accept(remover);
	}

	private final class TargetWalker extends GMLWalker {
		private HashSet<String> targets;

		@Override
		public void visit(AbstractSurface abstractSurface) {
			if (abstractSurface.isSetId())
				targets.add(new StringBuilder("#").append(abstractSurface.getId()).toString());

			super.visit(abstractSurface);
		}

		@Override
		public void visit(MultiSurface multiSurface) {
			if (multiSurface.isSetId())
				targets.add(new StringBuilder("#").append(multiSurface.getId()).toString());

			super.visit(multiSurface);
		}
	}

	private final class RemoverWalker extends FeatureWalker {
		private RemoverState state;
		private HashSet<String> targets;
		private HashSet<String> xlinks;

		@Override
		public void visit(Appearance appearance) {
			if (state == RemoverState.REMOVE_TARGETS) {
				Iterator<SurfaceDataProperty> surfaceDataIter = appearance.getSurfaceDataMember().iterator();
				while (surfaceDataIter.hasNext()) {
					SurfaceDataProperty surfaceDataMember = surfaceDataIter.next();
					if (!surfaceDataMember.isSetSurfaceData())
						continue;

					AbstractSurfaceData surfaceData = surfaceDataMember.getSurfaceData();
					boolean remove = false;

					if (surfaceData instanceof ParameterizedTexture) {
						ParameterizedTexture texture = (ParameterizedTexture)surfaceData;
						Iterator<TextureAssociation> iter = texture.getTarget().iterator();
						while (iter.hasNext()) {
							TextureAssociation textureAssociation = iter.next();
							if (!targets.contains(textureAssociation.getUri()))
								iter.remove();
						}

						if (texture.isSetTarget()) {
							if (texture.hasLocalProperty(Internal.TEXTURE_IMAGE_XLINK))
								xlinkPool.addWork((DBXlink)texture.getLocalProperty(Internal.TEXTURE_IMAGE_XLINK));
						} else
							remove = true;

					} else if (surfaceData instanceof X3DMaterial) {
						X3DMaterial material = (X3DMaterial)surfaceData;
						Iterator<String> iter = material.getTarget().iterator();
						while (iter.hasNext()) {
							String target = iter.next();
							if (!targets.contains(target))
								iter.remove();
						}

						if (!material.isSetTarget())
							remove = true;

					} else if (surfaceData instanceof GeoreferencedTexture) {
						GeoreferencedTexture texture = (GeoreferencedTexture)surfaceData;
						Iterator<String> iter = texture.getTarget().iterator();
						while (iter.hasNext()) {
							String target = iter.next();
							if (!targets.contains(target))
								iter.remove();
						}

						if (texture.isSetTarget()) {
							if (texture.hasLocalProperty(Internal.TEXTURE_IMAGE_XLINK))
								xlinkPool.addWork((DBXlink)texture.getLocalProperty(Internal.TEXTURE_IMAGE_XLINK));
						} else
							remove = true;
					}

					if (remove) {
						xlinks.add(new StringBuilder("#").append(surfaceData.getId()).toString());
						surfaceDataIter.remove();
					}
				}
			}

			else if (state == RemoverState.REMOVE_XLINKS) {
				Iterator<SurfaceDataProperty> surfaceDataIter = appearance.getSurfaceDataMember().iterator();
				while (surfaceDataIter.hasNext()) {
					SurfaceDataProperty surfaceDataMember = surfaceDataIter.next();
					if (surfaceDataMember.isSetHref() && xlinks.contains(surfaceDataMember.getHref()))
						surfaceDataIter.remove();
				}
			}
			
			if (appearance.getSurfaceDataMember().size() == 0) {
				AppearanceProperty property = (AppearanceProperty)appearance.getParent();
				AbstractCityObject parent = (AbstractCityObject)property.getParent();
				parent.unsetAppearance(property);					
			}
		}
	}

}

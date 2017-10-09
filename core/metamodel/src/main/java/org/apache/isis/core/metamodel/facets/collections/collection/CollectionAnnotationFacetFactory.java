/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.metamodel.facets.collections.collection;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import org.apache.isis.applib.annotation.Collection;
import org.apache.isis.applib.annotation.Hidden;
import org.apache.isis.applib.annotation.NotPersisted;
import org.apache.isis.applib.services.eventbus.CollectionDomainEvent;
import org.apache.isis.core.commons.config.IsisConfiguration;
import org.apache.isis.core.metamodel.facetapi.FacetHolder;
import org.apache.isis.core.metamodel.facetapi.FacetUtil;
import org.apache.isis.core.metamodel.facetapi.FeatureType;
import org.apache.isis.core.metamodel.facetapi.MetaModelValidatorRefiner;
import org.apache.isis.core.metamodel.facets.Annotations;
import org.apache.isis.core.metamodel.facets.FacetFactoryAbstract;
import org.apache.isis.core.metamodel.facets.FacetedMethod;
import org.apache.isis.core.metamodel.facets.actcoll.typeof.TypeOfFacet;
import org.apache.isis.core.metamodel.facets.actcoll.typeof.TypeOfFacetInferredFromArray;
import org.apache.isis.core.metamodel.facets.actcoll.typeof.TypeOfFacetInferredFromGenerics;
import org.apache.isis.core.metamodel.facets.all.hide.HiddenFacet;
import org.apache.isis.core.metamodel.facets.collections.collection.disabled.DisabledFacetForCollectionAnnotation;
import org.apache.isis.core.metamodel.facets.collections.collection.hidden.HiddenFacetForCollectionAnnotation;
import org.apache.isis.core.metamodel.facets.collections.collection.hidden.HiddenFacetForHiddenAnnotationOnCollection;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionAddToFacetForDomainEventFromAbstract;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionAddToFacetForDomainEventFromCollectionAnnotation;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionAddToFacetForDomainEventFromDefault;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionDomainEventFacetAbstract;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionDomainEventFacetDefault;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionDomainEventFacetForCollectionAnnotation;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionRemoveFromFacetForDomainEventFromAbstract;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionRemoveFromFacetForDomainEventFromCollectionAnnotation;
import org.apache.isis.core.metamodel.facets.collections.collection.modify.CollectionRemoveFromFacetForDomainEventFromDefault;
import org.apache.isis.core.metamodel.facets.collections.collection.notpersisted.NotPersistedFacetForCollectionAnnotation;
import org.apache.isis.core.metamodel.facets.collections.collection.notpersisted.NotPersistedFacetForNotPersistedAnnotationOnCollection;
import org.apache.isis.core.metamodel.facets.collections.collection.typeof.TypeOfFacetOnCollectionFromCollectionAnnotation;
import org.apache.isis.core.metamodel.facets.collections.modify.CollectionAddToFacet;
import org.apache.isis.core.metamodel.facets.collections.modify.CollectionRemoveFromFacet;
import org.apache.isis.core.metamodel.facets.members.disabled.DisabledFacet;
import org.apache.isis.core.metamodel.facets.propcoll.accessor.PropertyOrCollectionAccessorFacet;
import org.apache.isis.core.metamodel.facets.propcoll.notpersisted.NotPersistedFacet;
import org.apache.isis.core.metamodel.services.ServicesInjector;
import org.apache.isis.core.metamodel.specloader.CollectionUtils;
import org.apache.isis.core.metamodel.specloader.validator.MetaModelValidatorComposite;
import org.apache.isis.core.metamodel.specloader.validator.MetaModelValidatorForDeprecatedAnnotation;
import org.apache.isis.core.metamodel.util.EventUtil;

public class CollectionAnnotationFacetFactory extends FacetFactoryAbstract implements MetaModelValidatorRefiner {

    private final MetaModelValidatorForDeprecatedAnnotation hiddenValidator = new MetaModelValidatorForDeprecatedAnnotation(Hidden.class);
    private final MetaModelValidatorForDeprecatedAnnotation notPersistedValidator = new MetaModelValidatorForDeprecatedAnnotation(NotPersisted.class);


    public CollectionAnnotationFacetFactory() {
        super(FeatureType.COLLECTIONS_AND_ACTIONS);
    }

    @Override
    public void process(final ProcessMethodContext processMethodContext) {
        processModify(processMethodContext);
        processHidden(processMethodContext);
        processEditing(processMethodContext);
        processNotPersisted(processMethodContext);
        processTypeOf(processMethodContext);
    }

    void processModify(final ProcessMethodContext processMethodContext) {

        final Method method = processMethodContext.getMethod();
        final FacetHolder holder = processMethodContext.getFacetHolder();

        final PropertyOrCollectionAccessorFacet getterFacet = holder.getFacet(PropertyOrCollectionAccessorFacet.class);
        if(getterFacet == null) {
            return;
        }

        //
        // Set up CollectionDomainEventFacet, which will act as the hiding/disabling/validating advisor
        //
        final Collection collection = Annotations.getAnnotation(method, Collection.class);
        final Class<? extends CollectionDomainEvent<?, ?>> collectionDomainEventType;

        final CollectionDomainEventFacetAbstract collectionDomainEventFacet;


        // search for @Collection(domainEvent=...)
        if(collection != null) {
            collectionDomainEventType = collection.domainEvent();
            collectionDomainEventFacet = new CollectionDomainEventFacetForCollectionAnnotation(
                    collectionDomainEventType, servicesInjector, getSpecificationLoader(), holder);

        } else
        // else use default event type
        {
            collectionDomainEventType = CollectionDomainEvent.Default.class;
            collectionDomainEventFacet = new CollectionDomainEventFacetDefault(
                    collectionDomainEventType, servicesInjector, getSpecificationLoader(), holder);
        }
        if(!CollectionDomainEvent.Noop.class.isAssignableFrom(collectionDomainEventFacet.getEventType())) {
            FacetUtil.addFacet(collectionDomainEventFacet);
        }
        if(EventUtil.eventTypeIsPostable(
                collectionDomainEventFacet.getEventType(),
                CollectionDomainEvent.Noop.class,
                CollectionDomainEvent.Default.class,
                "isis.reflector.facet.collectionAnnotation.domainEvent.postForDefault", getConfiguration())) {
            FacetUtil.addFacet(collectionDomainEventFacet);
        }


        //
        // if the collection is mutable, then replace the existing addTo and removeFrom facets with equivalents that
        // also post to the event bus.
        //
        // here we support the deprecated annotations
        //
        final CollectionAddToFacet collectionAddToFacet = holder.getFacet(CollectionAddToFacet.class);
        if (collectionAddToFacet != null) {
            // the current collectionAddToFacet will end up as the underlying facet of
            // one of these facets to be created.
            final CollectionAddToFacetForDomainEventFromAbstract replacementFacet;

            if(collection != null) {
                replacementFacet = new CollectionAddToFacetForDomainEventFromCollectionAnnotation(
                        collectionDomainEventType, getterFacet, collectionAddToFacet, collectionDomainEventFacet, holder, servicesInjector);
            } else
            // default
            {
                replacementFacet = new CollectionAddToFacetForDomainEventFromDefault(
                        collectionDomainEventType, getterFacet, collectionAddToFacet, collectionDomainEventFacet, holder, servicesInjector);
            }
            FacetUtil.addFacet(replacementFacet);
        }

        final CollectionRemoveFromFacet collectionRemoveFromFacet = holder.getFacet(CollectionRemoveFromFacet.class);
        if (collectionRemoveFromFacet != null) {
            // the current collectionRemoveFromFacet will end up as the underlying facet of the PostsCollectionRemovedFromEventFacetAnnotation

            final CollectionRemoveFromFacetForDomainEventFromAbstract replacementFacet;

            if(collection != null) {
                replacementFacet = new CollectionRemoveFromFacetForDomainEventFromCollectionAnnotation(collectionDomainEventType, getterFacet, collectionRemoveFromFacet, collectionDomainEventFacet, servicesInjector, holder);
            } else
            // default
            {
                replacementFacet = new CollectionRemoveFromFacetForDomainEventFromDefault(collectionDomainEventType, getterFacet, collectionRemoveFromFacet, collectionDomainEventFacet, servicesInjector, holder);
            }
            FacetUtil.addFacet(replacementFacet);
        }

    }

    void processHidden(final ProcessMethodContext processMethodContext) {
        final Method method = processMethodContext.getMethod();
        final FacetHolder holder = processMethodContext.getFacetHolder();

        // check for deprecated @Hidden
        final Hidden hiddenAnnotation = Annotations.getAnnotation(processMethodContext.getMethod(), Hidden.class);
        HiddenFacet facet = hiddenValidator.flagIfPresent(HiddenFacetForHiddenAnnotationOnCollection.create(hiddenAnnotation, holder), processMethodContext);

        // else check for @Collection(hidden=...)
        final Collection collection = Annotations.getAnnotation(method, Collection.class);
        if(facet == null) {
            facet = HiddenFacetForCollectionAnnotation.create(collection, holder);
        }

        FacetUtil.addFacet(facet);
    }

    void processEditing(final ProcessMethodContext processMethodContext) {
        final Method method = processMethodContext.getMethod();
        final FacetHolder holder = processMethodContext.getFacetHolder();

        // check for @Collection(editing=...)
        final Collection collection = Annotations.getAnnotation(method, Collection.class);
        DisabledFacet facet = DisabledFacetForCollectionAnnotation.create(collection, holder);

        FacetUtil.addFacet(facet);
    }

    void processNotPersisted(final ProcessMethodContext processMethodContext) {
        final Method method = processMethodContext.getMethod();
        final FacetHolder holder = processMethodContext.getFacetHolder();

        // check for deprecated @NotPersisted first
        final NotPersisted annotation = Annotations.getAnnotation(method, NotPersisted.class);
        final NotPersistedFacet facet1 = NotPersistedFacetForNotPersistedAnnotationOnCollection.create(annotation, holder);
        FacetUtil.addFacet(notPersistedValidator.flagIfPresent(facet1, processMethodContext));
        NotPersistedFacet facet = facet1;

        // else search for @Collection(notPersisted=...)
        final Collection collection = Annotations.getAnnotation(method, Collection.class);
        if(facet == null) {
            facet = NotPersistedFacetForCollectionAnnotation.create(collection, holder);
        }

        FacetUtil.addFacet(facet);
    }


    void processTypeOf(final ProcessMethodContext processMethodContext) {

        final FacetedMethod facetHolder = processMethodContext.getFacetHolder();
        final Method method = processMethodContext.getMethod();

        final Class<?> methodReturnType = method.getReturnType();
        if (!CollectionUtils.isCollectionType(methodReturnType) && !CollectionUtils.isArrayType(methodReturnType)) {
            return;
        }

        // check for @Collection(typeOf=...)
        final Collection collection = Annotations.getAnnotation(method, Collection.class);
        TypeOfFacet facet = TypeOfFacetOnCollectionFromCollectionAnnotation
                .create(collection, facetHolder, getSpecificationLoader());

        // else infer from return type
        if(facet == null) {
            final Class<?> returnType = method.getReturnType();
            if (returnType.isArray()) {
                final Class<?> componentType = returnType.getComponentType();
                facet = new TypeOfFacetInferredFromArray(componentType, facetHolder, getSpecificationLoader());
            }
        }

        // else infer from generic return type
        if(facet == null) {
            facet = inferFromGenericReturnType(processMethodContext);
        }

        FacetUtil.addFacet(facet);
    }

    private TypeOfFacet inferFromGenericReturnType(final ProcessMethodContext processMethodContext) {

        final FacetedMethod facetHolder = processMethodContext.getFacetHolder();
        final Method method = processMethodContext.getMethod();

        final Type type = method.getGenericReturnType();
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        final ParameterizedType parameterizedType = (ParameterizedType) type;
        final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments.length == 0) {
            return null;
        }

        final Object actualTypeArgument = actualTypeArguments[0];
        if (actualTypeArgument instanceof Class) {
            final Class<?> actualType = (Class<?>) actualTypeArgument;
            return new TypeOfFacetInferredFromGenerics(actualType, facetHolder, getSpecificationLoader());
        }

        if (actualTypeArgument instanceof TypeVariable) {

            // TODO: what to do here?
            return null;
        }

        return null;
    }


    // //////////////////////////////////////

    @Override
    public void refineMetaModelValidator(final MetaModelValidatorComposite metaModelValidator, final IsisConfiguration configuration) {
        metaModelValidator.add(notPersistedValidator);
        metaModelValidator.add(hiddenValidator);
    }

    // //////////////////////////////////////


    @Override
    public void setServicesInjector(final ServicesInjector servicesInjector) {
        super.setServicesInjector(servicesInjector);
        final IsisConfiguration configuration = getConfiguration();

        notPersistedValidator.setConfiguration(configuration);
        hiddenValidator.setConfiguration(configuration);
    }


}

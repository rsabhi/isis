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
package org.apache.isis.core.transaction.changetracking;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import org.apache.isis.applib.annotation.EntityChangeKind;
import org.apache.isis.applib.events.lifecycle.AbstractLifecycleEvent;
import org.apache.isis.applib.services.bookmark.Bookmark;
import org.apache.isis.applib.services.eventbus.EventBusService;
import org.apache.isis.applib.services.iactn.Interaction;
import org.apache.isis.applib.services.iactn.InteractionContext;
import org.apache.isis.applib.services.metrics.MetricsService;
import org.apache.isis.applib.services.publishing.spi.EntityChanges;
import org.apache.isis.applib.services.publishing.spi.EntityPropertyChange;
import org.apache.isis.applib.services.xactn.TransactionId;
import org.apache.isis.commons.collections.Can;
import org.apache.isis.commons.internal.debug._Probe;
import org.apache.isis.commons.internal.exceptions._Exceptions;
import org.apache.isis.commons.internal.factory._InstanceUtil;
import org.apache.isis.core.metamodel.facets.object.callbacks.CallbackFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.LifecycleEventFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.LoadedCallbackFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.LoadedLifecycleEventFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.PersistedCallbackFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.PersistedLifecycleEventFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.PersistingCallbackFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.PersistingLifecycleEventFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.RemovingCallbackFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.RemovingLifecycleEventFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.UpdatedCallbackFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.UpdatedLifecycleEventFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.UpdatingCallbackFacet;
import org.apache.isis.core.metamodel.facets.object.callbacks.UpdatingLifecycleEventFacet;
import org.apache.isis.core.metamodel.facets.object.publish.entitychange.EntityChangePublishingFacet;
import org.apache.isis.core.metamodel.spec.ManagedObject;
import org.apache.isis.core.metamodel.spec.ManagedObjects;
import org.apache.isis.core.metamodel.spec.feature.MixedIn;
import org.apache.isis.core.security.authentication.AuthenticationContext;
import org.apache.isis.core.transaction.changetracking.events.IsisTransactionPlaceholder;
import org.apache.isis.core.transaction.events.TransactionBeforeCompletionEvent;

import lombok.NonNull;
import lombok.val;
import lombok.extern.log4j.Log4j2;

/**
 * @since 2.0 {@index}
 */
@Service
@Named("isis.transaction.EntityChangeTrackerDefault")
@Log4j2
public class EntityChangeTrackerDefault
implements
    MetricsService,
    EntityChangeTracker,
    HasEnlistedEntityPropertyChanges,
    HasEnlistedEntityChanges {

    @Inject private EntityPropertyChangePublisher entityPropertyChangePublisher;
    @Inject private EntityChangesPublisher entityChangesPublisher;
    @Inject private EventBusService eventBusService;
    @Inject private Provider<InteractionContext> interactionContextProvider;
    @Inject private Provider<AuthenticationContext> authenticationContextProvider;
    
    private ThreadLocal<_TransactionScopedContext> transactionScopedContext 
        = ThreadLocal.withInitial(()->
                new _TransactionScopedContext(interactionContextProvider, authenticationContextProvider));

    private boolean isEnlisted(final @NonNull ManagedObject adapter) {
        return ManagedObjects.bookmark(adapter)
        .map(bookmark->transactionScopedContext.get()
                .changeKindByEnlistedAdapter()
                .containsKey(bookmark))
        .orElse(false);
    }

    private void enlistCreatedInternal(final @NonNull ManagedObject entity) {
        onEntityChangeRecognized();
        if(isEntityEnabledForChangePublishing(entity)) {
            transactionScopedContext.get()
                .enlistForChangeKindPublishing(entity, EntityChangeKind.CREATE);
            enlistForPreAndPostValuePublishing(entity, record->record.setPreValue(IsisTransactionPlaceholder.NEW));    
        }
    }

    private void enlistUpdatingInternal(final @NonNull ManagedObject entity) {
        onEntityChangeRecognized();
        if(isEntityEnabledForChangePublishing(entity)) {
            transactionScopedContext.get()
                .enlistForChangeKindPublishing(entity, EntityChangeKind.UPDATE);
            enlistForPreAndPostValuePublishing(entity, _PropertyChangeRecord::updatePreValue);    
        }
    }

    private void enlistDeletingInternal(final @NonNull ManagedObject entity) {
        onEntityChangeRecognized();
        if(isEntityEnabledForChangePublishing(entity)) {
            val successfullyEnlisted = transactionScopedContext.get()
                    .enlistForChangeKindPublishing(entity, EntityChangeKind.DELETE);
            if(successfullyEnlisted) {
                enlistForPreAndPostValuePublishing(entity, _PropertyChangeRecord::updatePreValue);            
            }   
        }
    }

    private void onEntityChangeRecognized() {
        if(transactionScopedContext.get().isAlreadyPreparedForPublishing()) {
            throw _Exceptions.illegalState("Cannot enlist additional changes for auditing, "
                    + "since changedObjectPropertiesRef was already prepared (memoized) for auditing.");
        }
        entityChangeEventCount.increment();
        enableCommandPublishing();
    }
    
    boolean isEntityEnabledForChangePublishing(final @NonNull ManagedObject entity) {
        // ignore entities that are not enabled for entity change publishing
        return EntityChangePublishingFacet.isPublishingEnabled(entity.getSpecification());
    }

    /**
     * TRANSACTION END BOUNDARY
     * @apiNote intended to be called during before transaction completion by the framework internally
     */
    @EventListener(value = TransactionBeforeCompletionEvent.class)
    public void onTransactionCompleting(TransactionBeforeCompletionEvent event) {
        try {
            doPublish();
        } finally {
            postPublishing();    
        }
    }
    
    private void doPublish() {
        _Xray.publish(this, interactionContextProvider, authenticationContextProvider);
        
        log.debug("about to publish entity changes");
        entityPropertyChangePublisher.publishChangedProperties(this);
        entityChangesPublisher.publishChangingEntities(this);
    }

    private void postPublishing() {
        debug("PURGE");
        log.debug("purging entity change records");
        transactionScopedContext.remove();
        entityChangeEventCount.reset();
        numberEntitiesLoaded.reset();
    }

    private void debug(String label) {
        _Probe.errOut("!!![%s] %s %d", 
                Integer.toHexString(EntityChangeTrackerDefault.this.hashCode()),
                label, 
                transactionScopedContext.get().entityPropertyChangeRecords().size());
    }

    private void enableCommandPublishing() {
        val alreadySet = persitentChangesEncountered.getAndSet(true);
        if(!alreadySet) {
            val command = currentInteraction().getCommand();
            command.updater().setSystemStateChanged(true);
        }
    }

    @Override
    public Optional<EntityChanges> getEntityChanges(
            final java.sql.Timestamp timestamp,
            final String userName) {
        return _ChangingEntitiesFactory.createChangingEntities(timestamp, userName, this);
    }

    Interaction currentInteraction() {
        return interactionContextProvider.get().currentInteractionElseFail();
    }

    // -- HELPER

    private void enlistForPreAndPostValuePublishing(
            final ManagedObject entity,
            final Consumer<_PropertyChangeRecord> onNewRecord) {

        log.debug("enlist entity's property changes for publishing {}", entity);

        val entityPropertyChangeRecords = transactionScopedContext.get()
                .entityPropertyChangeRecords();
        entity.getSpecification().streamProperties(MixedIn.EXCLUDED)
        .filter(property->!property.isNotPersisted())
        .map(property->_PropertyChangeRecord.of(entity, property))
        .filter(record->!entityPropertyChangeRecords.contains(record)) // already enlisted, so ignore
        .forEach(record->{
            onNewRecord.accept(record);
            debug("ADD");
            entityPropertyChangeRecords.add(record);
            debug("ADDED");
        });
    }

    // -- METRICS SERVICE

    @Override
    public int numberEntitiesLoaded() {
        return Math.toIntExact(numberEntitiesLoaded.longValue());
    }

    @Override
    public int numberEntitiesDirtied() {
        return transactionScopedContext.get().numberEntitiesDirtied();
    }
    
    int propertyChangeRecordCount() {
        return transactionScopedContext.get().propertyChangeRecordCount();
    }

    // -- ENTITY CHANGE TRACKING

    @Override
    public void enlistCreated(ManagedObject entity) {
        _Xray.enlistCreated(entity, interactionContextProvider, authenticationContextProvider);
        val hasAlreadyBeenEnlisted = isEnlisted(entity);
        enlistCreatedInternal(entity);

        if(!hasAlreadyBeenEnlisted) {
            CallbackFacet.Util.callCallback(entity, PersistedCallbackFacet.class);
            postLifecycleEventIfRequired(entity, PersistedLifecycleEventFacet.class);
        }
    }

    @Override
    public void enlistDeleting(ManagedObject entity) {
        _Xray.enlistDeleting(entity, interactionContextProvider, authenticationContextProvider);
        enlistDeletingInternal(entity);
        CallbackFacet.Util.callCallback(entity, RemovingCallbackFacet.class);
        postLifecycleEventIfRequired(entity, RemovingLifecycleEventFacet.class);
    }

    @Override
    public void enlistUpdating(ManagedObject entity) {
        
        _Exceptions.dumpStackTrace();
        
        _Xray.enlistUpdating(entity, interactionContextProvider, authenticationContextProvider);
        val hasAlreadyBeenEnlisted = isEnlisted(entity);
        // we call this come what may;
        // additional properties may now have been changed, and the changeKind for publishing might also be modified
        enlistUpdatingInternal(entity);

        if(!hasAlreadyBeenEnlisted) {
            // prevent an infinite loop... don't call the 'updating()' callback on this object if we have already done so
            CallbackFacet.Util.callCallback(entity, UpdatingCallbackFacet.class);
            postLifecycleEventIfRequired(entity, UpdatingLifecycleEventFacet.class);
        }
    }

    @Override
    public void recognizeLoaded(ManagedObject entity) {
        _Xray.recognizeLoaded(entity, interactionContextProvider, authenticationContextProvider);
        CallbackFacet.Util.callCallback(entity, LoadedCallbackFacet.class);
        postLifecycleEventIfRequired(entity, LoadedLifecycleEventFacet.class);
        numberEntitiesLoaded.increment();
    }

    @Override
    public void recognizePersisting(ManagedObject entity) {
        _Xray.recognizePersisting(entity, interactionContextProvider, authenticationContextProvider);        
        CallbackFacet.Util.callCallback(entity, PersistingCallbackFacet.class);
        postLifecycleEventIfRequired(entity, PersistingLifecycleEventFacet.class);
    }

    @Override
    public void recognizeUpdating(ManagedObject entity) {
        _Xray.recognizeUpdating(entity, interactionContextProvider, authenticationContextProvider);
        CallbackFacet.Util.callCallback(entity, UpdatedCallbackFacet.class);
        postLifecycleEventIfRequired(entity, UpdatedLifecycleEventFacet.class);
    }

    private final LongAdder numberEntitiesLoaded = new LongAdder();
    private final LongAdder entityChangeEventCount = new LongAdder();
    private final AtomicBoolean persitentChangesEncountered = new AtomicBoolean();

    //  -- HELPER

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void postLifecycleEventIfRequired(
            ManagedObject adapter,
            Class<? extends LifecycleEventFacet> lifecycleEventFacetClass) {

        val lifecycleEventFacet = adapter.getSpecification().getFacet(lifecycleEventFacetClass);
        if(lifecycleEventFacet == null) {
            return;
        }
        val eventInstance = (AbstractLifecycleEvent) _InstanceUtil
                .createInstance(lifecycleEventFacet.getEventType());
        val pojo = adapter.getPojo();
        postEvent(eventInstance, pojo);

    }

    private void postEvent(final AbstractLifecycleEvent<Object> event, final Object pojo) {
        if(eventBusService!=null) {
            event.initSource(pojo);
            eventBusService.post(event);
        }
    }

    @Override
    public Can<EntityPropertyChange> getPropertyChanges(
            final java.sql.Timestamp timestamp,
            final String userName,
            final TransactionId txId) {

        return transactionScopedContext.get()
                .snapshotPropertyChangeRecords()
                .stream()
                .map(propertyChangeRecord->_EntityPropertyChangeFactory
                        .createEntityPropertyChange(timestamp, userName, txId, propertyChangeRecord))
                .collect(Can.toCan());
    }

    Map<Bookmark, EntityChangeKind> changeKindByEnlistedAdapter() {
        return transactionScopedContext.get().changeKindByEnlistedAdapter();
    }

}

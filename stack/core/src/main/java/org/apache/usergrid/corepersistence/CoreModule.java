/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.usergrid.corepersistence;


import com.google.inject.*;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.Multibinder;
import org.apache.usergrid.corepersistence.asyncevents.*;
import org.apache.usergrid.corepersistence.export.ExportService;
import org.apache.usergrid.corepersistence.export.ExportServiceImpl;
import org.apache.usergrid.corepersistence.index.*;
import org.apache.usergrid.corepersistence.migration.CoreMigration;
import org.apache.usergrid.corepersistence.migration.CoreMigrationPlugin;
import org.apache.usergrid.corepersistence.migration.DeDupConnectionDataMigration;
import org.apache.usergrid.corepersistence.pipeline.PipelineModule;
import org.apache.usergrid.corepersistence.rx.impl.*;
import org.apache.usergrid.corepersistence.service.*;
import org.apache.usergrid.locking.guice.LockModule;
import org.apache.usergrid.persistence.cache.guice.CacheModule;
import org.apache.usergrid.persistence.collection.guice.CollectionModule;
import org.apache.usergrid.persistence.collection.serialization.impl.migration.EntityIdScope;
import org.apache.usergrid.persistence.core.executor.TaskExecutorFactory;
import org.apache.usergrid.persistence.core.guice.CommonModule;
import org.apache.usergrid.persistence.core.migration.data.DataMigration;
import org.apache.usergrid.persistence.core.migration.data.MigrationDataProvider;
import org.apache.usergrid.persistence.core.migration.data.MigrationPlugin;
import org.apache.usergrid.persistence.core.rx.RxTaskScheduler;
import org.apache.usergrid.persistence.core.rx.RxTaskSchedulerImpl;
import org.apache.usergrid.persistence.core.scope.ApplicationScope;
import org.apache.usergrid.persistence.graph.guice.GraphModule;
import org.apache.usergrid.persistence.graph.serialization.impl.migration.GraphNode;
import org.apache.usergrid.persistence.index.guice.IndexModule;
import org.apache.usergrid.persistence.token.guice.TokenModule;
import org.safehaus.guicyfig.GuicyFigModule;

import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * Guice Module that encapsulates Core Persistence.
 */
public class CoreModule extends AbstractModule {

    private final Properties properties;

    public CoreModule( Properties properties ) {
        this.properties = properties;
    }

    @Override
    protected void configure() {

        install( new CommonModule());
        install( new LockModule());
        install( new CacheModule());
        install( new TokenModule());
        install( new CollectionModule() {
            /**
             * configure our migration data provider for all entities in the system
             */
            @Override
            public void configureMigrationProvider() {

                bind( new TypeLiteral<MigrationDataProvider<EntityIdScope>>() {} ).to( AllEntitiesInSystemImpl.class );
            }
        } );
        install( new GraphModule() {

            /**
             * Override the observable that needs to be used for migration
             */
            @Override
            public void configureMigrationProvider() {
                bind( new TypeLiteral<MigrationDataProvider<GraphNode>>() {} ).to( AllNodesInGraphImpl.class );
            }
        } );

        install( new IndexModule( properties ) {
            @Override
            public void configureMigrationProvider() {
                bind( new TypeLiteral<MigrationDataProvider<ApplicationScope>>() {} )
                    .to( AllApplicationsObservableImpl.class );
            }
        } );
        //        install(new MapModule());   TODO, re-enable when index module doesn't depend on queue
        //        install(new QueueModule());

        bind( ManagerCache.class ).to( CpManagerCache.class );
        bind( ApplicationIdCacheFactory.class );
        bind( CollectionSettingsFactory.class );
        bind( CollectionSettingsCache.class );
        bind( CollectionVersionManagerFactory.class );
        bind( CollectionVersionCache.class );


        /**
         * Create our migrations for within our core plugin
         */
        Multibinder<DataMigration> dataMigrationMultibinder =
            Multibinder.newSetBinder( binder(), new TypeLiteral<DataMigration>() {}, CoreMigration.class );


        dataMigrationMultibinder.addBinding().to( DeDupConnectionDataMigration.class );


        //wire up the collection migration plugin
        final Multibinder<MigrationPlugin> plugins = Multibinder.newSetBinder( binder(), MigrationPlugin.class );
        plugins.addBinding().to( CoreMigrationPlugin.class );

        bind( AllApplicationsObservable.class ).to( AllApplicationsObservableImpl.class );
        bind( AllEntityIdsObservable.class ).to( AllEntityIdsObservableImpl.class );


        /*****
         * Indexing service
         *****/


        bind( IndexService.class ).to( IndexServiceImpl.class );

        //bind the event handlers
        bind( EventBuilder.class ).to( EventBuilderImpl.class );
        bind( ApplicationIndexBucketLocator.class );

        //bind the queue provider
        bind( AsyncEventService.class ).toProvider( AsyncIndexProvider.class );


        bind( ReIndexService.class ).to( ReIndexServiceImpl.class );

        bind( CollectionDeleteService.class ).to( CollectionDeleteServiceImpl.class );

        bind( ExportService.class ).to( ExportServiceImpl.class );

        install( new FactoryModuleBuilder().implement( AggregationService.class, AggregationServiceImpl.class )
                                           .build( AggregationServiceFactory.class ) );

        bind( IndexLocationStrategyFactory.class ).to( IndexLocationStrategyFactoryImpl.class );

        install( new GuicyFigModule( IndexProcessorFig.class ) );

        install( new GuicyFigModule( CoreIndexFig.class ) );


        install( new GuicyFigModule( ApplicationIdCacheFig.class ) );

        install( new GuicyFigModule( CollectionSettingsCacheFig.class ) );

        install( new GuicyFigModule( CollectionVersionFig.class ) );

        install( new GuicyFigModule( EntityManagerFig.class ) );

        install( new GuicyFigModule( AsyncEventsSchedulerFig.class ) );

        install( new GuicyFigModule( ServiceSchedulerFig.class ) );

        //install our pipeline modules
        install( new PipelineModule() );

        /**
         * Install our service operations
         */

        bind( CollectionService.class ).to( CollectionServiceImpl.class );

        bind( ConnectionService.class ).to( ConnectionServiceImpl.class );

        bind( ApplicationService.class ).to( ApplicationServiceImpl.class );

        bind( StatusService.class ).to( StatusServiceImpl.class );
    }


    @Provides
    @Inject
    @EventExecutionScheduler
    @Singleton
    public RxTaskScheduler getSqsTaskScheduler( final AsyncEventsSchedulerFig asyncEventsSchedulerFig ) {

        final String poolName = asyncEventsSchedulerFig.getIoSchedulerName();
        final int threadCount = asyncEventsSchedulerFig.getMaxIoThreads();


        final ThreadPoolExecutor executor = TaskExecutorFactory
            .createTaskExecutor( poolName, threadCount, threadCount, TaskExecutorFactory.RejectionAction.CALLERRUNS );

        final RxTaskScheduler taskScheduler = new RxTaskSchedulerImpl( executor );

        return taskScheduler;
    }


    @Provides
    @Inject
    @AsyncRepair
    @Singleton
    public RxTaskScheduler getAsyncRepairScheduler( final AsyncEventsSchedulerFig asyncEventsSchedulerFig ) {

        final String poolName = asyncEventsSchedulerFig.getRepairPoolName();
        final int threadCount = asyncEventsSchedulerFig.getMaxRepairThreads();


        final ThreadPoolExecutor executor = TaskExecutorFactory
            .createTaskExecutor( poolName, threadCount, 0, TaskExecutorFactory.RejectionAction.DROP );

        final RxTaskScheduler taskScheduler = new RxTaskSchedulerImpl( executor );

        return taskScheduler;
    }


    @Provides
    @Inject
    @ResponseImportTasks
    @Singleton
    public RxTaskScheduler getResponseImportScheduler(final ServiceSchedulerFig serviceSchedulerFig ) {

        final String poolName = serviceSchedulerFig.getRepairPoolName();
        final int threadCount = serviceSchedulerFig.getImportThreadPoolSize();


        final ThreadPoolExecutor executor = TaskExecutorFactory
            .createTaskExecutor( poolName, threadCount, 0, TaskExecutorFactory.RejectionAction.CALLERRUNS );

        final RxTaskScheduler taskScheduler = new RxTaskSchedulerImpl( executor );

        return taskScheduler;
    }
}

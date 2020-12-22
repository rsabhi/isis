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
package org.apache.isis.testdomain.transactions.jdo.spring;

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.applib.services.xactn.TransactionService;
import org.apache.isis.core.config.presets.IsisPresets;
import org.apache.isis.core.interaction.session.InteractionFactory;
import org.apache.isis.testdomain.conf.Configuration_usingJdoSpring;
import org.apache.isis.testdomain.jdo.JdoTestDomainPersona;
import org.apache.isis.testdomain.jdo.entities.JdoBook;
import org.apache.isis.testdomain.util.interaction.InteractionBoundaryProbe;
import org.apache.isis.testdomain.util.kv.KVStoreForTesting;
import org.apache.isis.testing.fixtures.applib.fixturescripts.FixtureScripts;

@SpringBootTest(
        classes = { 
                Configuration_usingJdoSpring.class,
                InteractionBoundaryProbe.class
        })
@TestPropertySource(IsisPresets.UseLog4j2Test)
/**
 * With this test we manage IsisInteractions ourselves. (not sub-classing IsisIntegrationTestAbstract)
 */
class JdoSpringTransactionScopeListenerTest {
    
    @Inject private FixtureScripts fixtureScripts;
    @Inject private TransactionService transactionService;
    @Inject private RepositoryService repository;
    @Inject private InteractionFactory isisInteractionFactory;
    @Inject private KVStoreForTesting kvStoreForTesting;
    
    /* Expectations:
     * 1. for each IsisInteractionScope there should be a new InteractionBoundaryProbe instance
     * 2. for each Transaction the current InteractionBoundaryProbe should get notified
     * 
     * first we have 1 IsisInteractionScope with 1 expected Transaction during 'setUp'
     * then we have 1 IsisInteractionScope with 3 expected Transactions within the test method
     *  
     */
    
    @BeforeEach
    void setUp() {
        
        // new IsisInteractionScope with a new transaction (#1)
        isisInteractionFactory.runAnonymous(()->{
        
            // cleanup
            fixtureScripts.runPersona(JdoTestDomainPersona.PurgeAll);
            
        });
        
    }
    
    @Test @Disabled("wip")
    void sessionScopedProbe_shouldBeReused_andBeAwareofTransactionBoundaries() {
        
        // new IsisInteractionScope
        isisInteractionFactory.runAnonymous(()->{
            
            // expected pre condition
            // new transaction (#2)
            assertEquals(0, repository.allInstances(JdoBook.class).size());
        
            // new transaction (#3)
            transactionService.executeWithinTransaction(()->{
                
                fixtureScripts.runPersona(JdoTestDomainPersona.InventoryWith1Book);
                
            });
            
            // expected post condition
            // new transaction (#4)
            assertEquals(1, repository.allInstances(JdoBook.class).size());
            
        });
        
        assertEquals(2, InteractionBoundaryProbe.totalInteractionsStarted(kvStoreForTesting));
        assertEquals(2, InteractionBoundaryProbe.totalInteractionsEnded(kvStoreForTesting));
        assertEquals(4, InteractionBoundaryProbe.totalTransactionsStarted(kvStoreForTesting));
        assertEquals(4, InteractionBoundaryProbe.totalTransactionsEnded(kvStoreForTesting));

    }
    

}

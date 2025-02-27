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
package org.apache.isis.extensions.secman.model.dom.tenancy;

import javax.inject.Inject;

import org.apache.isis.applib.annotation.Action;
import org.apache.isis.applib.annotation.ActionLayout;
import org.apache.isis.applib.annotation.MemberSupport;
import org.apache.isis.applib.annotation.Optionality;
import org.apache.isis.applib.annotation.Parameter;
import org.apache.isis.extensions.secman.api.tenancy.ApplicationTenancy;
import org.apache.isis.extensions.secman.api.tenancy.ApplicationTenancy.UpdateParentDomainEvent;
import org.apache.isis.extensions.secman.api.tenancy.ApplicationTenancyRepository;

import lombok.RequiredArgsConstructor;

@Action(
        domainEvent = UpdateParentDomainEvent.class, 
        associateWith = "parent")
@ActionLayout(sequence = "1")
@RequiredArgsConstructor
public class ApplicationTenancy_updateParent {
    
    @Inject private ApplicationTenancyRepository<? extends ApplicationTenancy> applicationTenancyRepository;
    
    private final ApplicationTenancy target;

    @MemberSupport
    public ApplicationTenancy act(
            @Parameter(optionality = Optionality.OPTIONAL)
            final ApplicationTenancy parent) {
        
        applicationTenancyRepository.setParentOnTenancy(target, parent);
        return target;
    }

    @MemberSupport
    public ApplicationTenancy default0Act() {
        return target.getParent();
    }
}

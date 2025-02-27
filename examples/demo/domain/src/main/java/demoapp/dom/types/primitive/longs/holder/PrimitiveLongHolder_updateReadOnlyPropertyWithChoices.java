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
package demoapp.dom.types.primitive.longs.holder;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.isis.applib.annotation.Action;
import org.apache.isis.applib.annotation.ActionLayout;
import org.apache.isis.applib.annotation.PromptStyle;
import org.apache.isis.applib.annotation.SemanticsOf;

import lombok.RequiredArgsConstructor;

import demoapp.dom.types.Samples;


//tag::class[]
@Action(
        semantics = SemanticsOf.IDEMPOTENT,
        associateWith = "readOnlyProperty"
)
@ActionLayout(promptStyle = PromptStyle.INLINE, named = "Update with choices", sequence = "2")
@RequiredArgsConstructor
public class PrimitiveLongHolder_updateReadOnlyPropertyWithChoices {

    private final PrimitiveLongHolder holder;

    public PrimitiveLongHolder act(long newValue) {
        holder.setReadOnlyProperty(newValue);
        return holder;
    }
    public long default0Act() {
        return holder.getReadOnlyProperty();
    }
    public List<Long> choices0Act() {
        return samples.stream()
                .collect(Collectors.toList());
    }
    public boolean hideAct() {
        return true; // TODO: choices doesn't seem to work for this datatype
    }

    @Inject
    Samples<Long> samples;

}
//end::class[]

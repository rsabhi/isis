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
package org.apache.isis.testdomain.jdo;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.apache.isis.applib.annotation.Action;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.NatureOfService;
import org.apache.isis.applib.annotation.ParameterLayout;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.core.commons.internal.base._NullSafe;
import org.apache.isis.core.commons.internal.collections._Lists;

import lombok.val;

@DomainService(
        nature = NatureOfService.REST,
        objectType = "testdomain.InventoryResource")
public class InventoryResource {

    @Inject private RepositoryService repository;
    
    @Action
    public List<Product> listProducts() {
        return repository.allInstances(Product.class);
    }

    @Action
    public List<Book> listBooks() {
        return repository.allInstances(Book.class);
    }

    @Action
    public Book recommendedBookOfTheWeek() {
        // for this test we do not care if we generate duplicates
        val book = Book.of("Book of the week", "An awesome Book", 12, "Author", "ISBN", "Publisher");
        return repository.persist(book);
    }

    @Action
    public List<Book> multipleBooks(
            
            @ParameterLayout(named = "")
            int nrOfBooks
            
            ) {
        
        val books = _Lists.<Book>newArrayList();
        
        // for this test we do not care if we generate duplicates
        for(int i=0; i<nrOfBooks; ++i) {
            val book = Book.of("MultipleBooksTest", "An awesome Book["+i+"]", 12, "Author", "ISBN", "Publisher");
            books.add(repository.persist(book));
        }
        return books;
    }
    
    @Action //TODO improve the REST client such that the param can be of type Book
    public Book storeBook(String newBook) throws JAXBException { 
        Book book = BookDto.decode(newBook).toBook();
        return repository.persist(book);
    }
    
    @Action
    public String httpSessionInfo() {
        
        // when running with basic-auth strategy, we don't want to create HttpSessions at all
        
        val servletRequestAttributes = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        val httpSession = servletRequestAttributes.getRequest().getSession(false);
        if(httpSession==null) {
            return "no http-session";
        }
        val sessionAttributeNames = _NullSafe.stream(httpSession.getAttributeNames())
        .collect(Collectors.joining(","));
        
        return String.format("http-session attribute names: {%s}", sessionAttributeNames);
    }

}

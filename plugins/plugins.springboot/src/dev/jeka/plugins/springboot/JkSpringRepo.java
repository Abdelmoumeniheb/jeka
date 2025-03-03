/*
 * Copyright 2014-2024  the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.jeka.plugins.springboot;


import dev.jeka.core.api.depmanagement.JkRepo;

/**
 * Download repositories of <i>Spring IO</i> company.
 */
public enum JkSpringRepo {

    SNAPSHOT("https://repo.spring.io/snapshot/"),
    MILESTONE("https://repo.spring.io/milestone/"),
    RELEASE("https://repo.spring.io/release/");

    private final String url;

    JkSpringRepo(String url) {
        this.url = url;
    }

    public JkRepo get() {
        return JkRepo.of(url);
    }

}

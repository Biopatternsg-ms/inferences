/*
 * Copyright © 2026 biopatternsg (biopatternsg@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.biopatternsg.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class PathwayEvent {
    private String id;
    private String first;
    private String relation;
    private String second;
    private Integer position;

    public PathwayEvent(String first, String relation, String second) {
        this(null, first, relation, second, null);
    }

    public PathwayEvent(String first, String relation, String second, Integer position) {
        this(null, first, relation, second, position);
    }

    public String getSubject() {
        return first;
    }

    public void setSubject(String subject) {
        this.first = subject;
    }

    public String getObject() {
        return second;
    }

    public void setObject(String object) {
        this.second = object;
    }

    public String toRawEvent() {
        return "'" + first + "'," + relation + ",'" + second + "'";
    }
}

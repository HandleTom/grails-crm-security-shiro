/*
 *  Copyright 2012 Goran Ehrsson.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package grails.plugins.crm.security.shiro

import grails.plugins.crm.core.AuditEntity

/**
 * This domain class represents a tenant, also known as "account".
 * A user can be associated with multiple tenants but only have one tenant active at a given time.
 *
 * @author Goran Ehrsson
 * @since 0.1
 */
@AuditEntity
class ShiroCrmTenant {

    // Long id of this account will be used as tenantId for all instances created by this tenant.
    String name
    String type
    static belongsTo = [user: ShiroCrmUser]
    static constraints = {
        name(size: 3..80, maxSize: 80, nullable: false, blank: false)
        type(maxSize: 20, nullable: true)
    }
    static mapping = {
        cache usage: 'nonstrict-read-write'
        sort "name"
    }

    static transients = ['dao']

    /**
     * Returns the name property.
     * @return name property
     */
    String toString() {
        name
    }

    /**
     * Clients should use this method to get tenant properties instead of accessing the domain instance directly.
     * The following properties are returned as a Map: [Long id, String name, String type, Map user [username, name, email]]
     * @return a data access object (Map) representing the domain instance.
     */
    def getDao() {
        [id: id, name: name, type: type, user: [username: user.username, name: user.name, email: user.email], dateCreated: dateCreated]
    }
}

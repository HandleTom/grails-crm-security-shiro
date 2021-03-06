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

import grails.plugins.crm.core.TenantUtils
import grails.plugins.crm.security.CrmAccount
import grails.plugins.crm.security.CrmRole
import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.UsernamePasswordToken

class ShiroCrmSecurityServiceSpec extends grails.test.spock.IntegrationSpec {

    def crmAccountService
    def crmSecurityService
    def grailsApplication

    def "runAs changes current user"() {
        def result

        given:
        crmSecurityService.createUser([username: "test8", name: "Test User", email: "test@test.com", password: "test123", enabled: true])

        when:
        crmSecurityService.runAs("test8") {
            result = crmSecurityService.getCurrentUser()
        }
        then:
        result != null
        result.username == "test8"
    }

    def "runAs will not authenticate the user"() {
        def result

        given:
        crmSecurityService.createUser([username: "test9", name: "Test User", email: "test@test.com", password: "test123", enabled: true])

        when:
        crmSecurityService.runAs("test9") {
            result = crmSecurityService.isAuthenticated()
        }
        then:
        result == false
    }

    def "authenticate the user with login"() {
        def result

        given:
        crmSecurityService.createUser([username: "test10", name: "Test User", email: "test@test.com", password: "test123", enabled: true])

        when:
        crmSecurityService.runAs("test10") {
            SecurityUtils.subject.login(new UsernamePasswordToken("test10", "test123"))
            result = crmSecurityService.isAuthenticated()
            SecurityUtils.subject.logout()
        }
        then:
        result == true
    }

    def "runAs with non-existing username should throw exception"() {
        def result
        // No user created here.
        when:
        crmSecurityService.runAs("test11") {
            result = crmSecurityService.getCurrentUser()
        }
        then:
        thrown(IllegalArgumentException)
    }

    def "runAs with disabled user should throw exception"() {
        def result

        given:
        crmSecurityService.createUser([username: "test12", name: "Test User", email: "test@test.com", password: "test123", enabled: false])

        when:
        crmSecurityService.runAs("test12") {
            result = crmSecurityService.getCurrentUser()
        }
        then:
        thrown(IllegalArgumentException)
    }

    def "wildcard permissions"() {

        def tenant
        def result

        given:
        crmSecurityService.createUser([username: "test13", name: "Test User", email: "test@test.com", password: "test123", enabled: true])
        crmSecurityService.addPermissionAlias("test", ["test:*"])

        when:
        crmSecurityService.runAs("test13") {
            def a = crmAccountService.createAccount(status: "active")
            tenant = crmSecurityService.createTenant(a, "Test Tenant")
        }
        then:
        tenant != null

        when:
        TenantUtils.withTenant(tenant.id) {
            crmSecurityService.runAs("test13") {
                SecurityUtils.subject.checkPermission("test:protected")
            }
        }
        then:
        thrown(org.apache.shiro.subject.ExecutionException)

        when:
        TenantUtils.withTenant(tenant.id) {
            crmSecurityService.runAs("test13") {
                result = crmSecurityService.isPermitted("test:protected")
            }
        }
        then:
        result == false


        when:
        TenantUtils.withTenant(tenant.id) {
            crmSecurityService.addPermissionToUser("test", "test13")
            crmSecurityService.runAs("test13") {
                result = crmSecurityService.isPermitted("test:protected")
                SecurityUtils.subject.checkPermission("test:protected")
            }
        }
        then:
        result == true

    }

    def "permission check with invalid tenant"() {

        def tenant
        def result

        given:
        crmSecurityService.createUser([username: "test14", name: "Test User", email: "test@test.com", password: "test123", enabled: true])
        crmSecurityService.addPermissionAlias("test", ["test:*"])

        when:
        crmSecurityService.runAs("test14") {
            def a = crmAccountService.createAccount(status: "active")
            tenant = crmSecurityService.createTenant(a, "Test Tenant")
            TenantUtils.withTenant(tenant.id) {
                crmSecurityService.addPermissionToUser("test", "test14")
            }
        }
        then:
        tenant != null

        when:
        crmSecurityService.runAs("test14") {
            TenantUtils.withTenant(tenant.id) {
                result = crmSecurityService.isPermitted("test:protected")
            }
        }
        then:
        result == true

        when:
        crmSecurityService.runAs("test14") {
            TenantUtils.withTenant(42L) {
                result = crmSecurityService.isPermitted("test:protected")
            }
        }
        then:
        result == false
    }

    def "create a new role"() {
        def tenant
        def result = []

        given:
        def user = crmSecurityService.createUser([username: "test15", name: "Test User", email: "test@test.com", password: "test123", enabled: true])
        crmSecurityService.addPermissionAlias("foo", ["foo:*"])
        crmSecurityService.addPermissionAlias("bar", ["bar:*"])
        crmSecurityService.addPermissionAlias("baz", ["baz:*"])

        when:
        crmSecurityService.runAs("test15") {
            def a = crmAccountService.createAccount([status: "active"], [crmTester: 1])
            tenant = crmSecurityService.createTenant(a, "Test Tenant")
            TenantUtils.withTenant(tenant.id) {
                crmSecurityService.createRole("tester", ["foo", "bar"])
                crmSecurityService.addUserRole(user, "tester")

                result << SecurityUtils.subject.hasRole("tester")
                result << crmSecurityService.isPermitted("foo:index")
                result << crmSecurityService.isPermitted("bar:index")
                result << crmSecurityService.isPermitted("baz:index") // Not included in this role
                result << crmSecurityService.isPermitted("xxx:index") // Non existing permission
            }
        }
        then:
        result == [true, true, true, false, false]

        when:
        result = []
        crmSecurityService.runAs("test15") {
            TenantUtils.withTenant(tenant.id) {
                crmSecurityService.addPermissionToRole("foo", "tester")
                crmSecurityService.addPermissionToRole("bar", "tester")
                crmSecurityService.addPermissionToRole("baz", "tester")

                result << SecurityUtils.subject.hasRole("tester")
                result << crmSecurityService.isPermitted("foo:index")
                result << crmSecurityService.isPermitted("bar:index")
                result << crmSecurityService.isPermitted("baz:index") // This time baz is included
                result << crmSecurityService.isPermitted("xxx:index") // Non existing permission
            }
        }

        then:
        result == [true, true, true, true, false]
    }

    def "create tenant with locale"() {

        given:
        def result = []
        def swedish = new Locale("sv", "SE")
        def spanish = new Locale("es", "ES")
        crmSecurityService.createUser([username: "test16", name: "Test User", email: "test@test.com", password: "test123", enabled: true])

        when:
        crmSecurityService.runAs("test16") {
            def a = crmAccountService.createAccount(status: "active")
            result << crmSecurityService.createTenant(a, "Default")
            result << crmSecurityService.createTenant(a, "Svenska", [locale: swedish])
            result << crmSecurityService.createTenant(a, "Español", [locale: spanish])
        }
        then:
        result[0].locale == null
        result[1].locale == swedish.toString()
        result[2].locale == spanish.toString()
    }

    def "test autocreated admin role"() {
        given:
        def securityConfig = grailsApplication.config.crm.security
        def tenant
        securityConfig.default.permission.guest = ["crmTenant:index,activate"]
        securityConfig.default.permission.user = ["crmTenant:index,activate,create,edit"]
        securityConfig.default.permission.admin = ["crmTenant:*"]
        crmSecurityService.createUser([username: "test17", name: "Test User", email: "test@test.com", password: "test123", enabled: true])

        when:
        crmSecurityService.runAs("test17") {
            def a = crmAccountService.createAccount(status: "active")
            tenant = crmSecurityService.createTenant(a, "Default")
        }

        then:
        tenant != null
        CrmRole.countByTenantId(tenant.id) == 1
        CrmRole.findByTenantId(tenant.id).name == 'admin'
    }

    def "make sure default tenant is correct"() {
        given: "create a user"
        def user = crmSecurityService.createUser([username: "test18", name: "Test User", email: "test@test.com", password: "test123", enabled: true])

        when: "create an account and a tenant"
        def t1 = crmSecurityService.runAs(user.username) {
            def privateAccount = crmAccountService.createAccount(status: "active")
            crmSecurityService.createTenant(privateAccount, "User 18's Private Tenant")
        }
        def a1 = t1.account

        then: "the account is active"
        a1.active == true
        t1.id != null

        when: "create a new account for the same user"
        def t2 = crmSecurityService.runAs(user.username) {
            def corporateAccount = crmAccountService.createAccount(status: "active")
            crmSecurityService.createTenant(corporateAccount, "User 18's Corporate Tenant")
        }
        def a2 = t2.account

        then: "this second account is also active from the start"
        a2.active == true
        t2.id != null

        when: "list all available tenants for the user"
        def availableTenants = crmSecurityService.getTenants(user.username)

        then: "both tenants should be active"
        availableTenants.size() == 2

        when: "close/expire the first account"
        a1.expires = new java.sql.Date((new Date() - 7).time)
        a1.status = CrmAccount.STATUS_CLOSED
        a1.name = "This account expired a week ago"
        a1.save(flush: true)

        then: "the account is not active anymore"
        a1.active == false

        when: "list all available tenants for the user"
        availableTenants = crmSecurityService.getTenants(user.username)

        then: "only one (the corporate) tenant should be active"
        availableTenants.size() == 1
        availableTenants[0].name == "User 18's Corporate Tenant"
    }
}

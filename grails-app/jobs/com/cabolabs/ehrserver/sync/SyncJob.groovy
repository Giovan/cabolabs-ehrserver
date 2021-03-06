/*
 * Copyright 2011-2017 CaboLabs Health Informatics
 *
 * The EHRServer was designed and developed by Pablo Pazos Gutierrez <pablo.pazos@cabolabs.com> at CaboLabs Health Informatics (www.cabolabs.com).
 *
 * You can't remove this notice from the source code, you can't remove the "Powered by CaboLabs" from the UI, you can't remove this notice from the window that appears then the "Powered by CaboLabs" link is clicked.
 *
 * Any modifications to the provided source code can be stated below this notice.
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

package com.cabolabs.ehrserver.sync

import com.cabolabs.ehrserver.sync.SyncMarshallersService
import groovy.json.*

import com.cabolabs.ehrserver.openehr.ehr.*
import com.cabolabs.ehrserver.account.*
import com.cabolabs.ehrserver.ehr.clinical_documents.*
import com.cabolabs.security.*
import com.cabolabs.ehrserver.openehr.common.change_control.*
import com.cabolabs.ehrserver.openehr.common.generic.*
import com.cabolabs.ehrserver.query.*
import com.cabolabs.ehrserver.openehr.directory.*

import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST
import java.net.*

class SyncJob {

   def concurrent = false

   static triggers = {
      simple repeatInterval: 20000l, startDelay: 60000l // execute job once in 5 seconds
   }

   def syncMarshallersService

   def execute()
   {
      // check sync config
      def remotes = SyncClusterConfig.findAllByIsActive(true)

      if (remotes.size() == 0) return

      int logCount = 0
      boolean error = false
      def log

      remotes.each { remote ->

         // sync accounts
         def accounts = Account.findAllByMaster(true)

         accounts.each { account ->

            logCount = SyncLog.countByResourceTypeAndResourceUidAndRemote('Account', account.uid, remote)

            // no logs => sync this account
            // TODO: there are logs but the resource was updated (lastUpdated) since the log.dateCreated => sync
            if (logCount == 0)
            {
               // TODO: move the sync logic to a service
               def jb = new JsonBuilder()
               syncMarshallersService.toJSON(account, jb)

               // FIXME: ensure HTTPS!
               def http = new HTTPBuilder('http://'+ remote.remoteServerIP +':'+ remote.remoteServerPort)

               try
               {
                  http.request(POST) {
                     uri.path = remote.remoteServerPath + 'syncAccount'
                     uri.query = [:]
                     send JSON, jb.toString()
                     headers.Accept = 'application/json'
                     headers.Authorization = 'Bearer '+ remote.remoteAPIKey

                     response.success = { resp, json ->
                        println "POST Success: ${resp.statusLine}" // POST Success: HTTP/1.1 200 OK
                        //println resp.statusLine.statusCode // 200
                        //println json.getClass() // class net.sf.json.JSONArray

                        println json //.message

                        /*
                        json.each { item ->
                           //println item.idconcept +' '+ item.concept

                           res << item.idconcept
                        }
                        */
                     }

                     // FIXME: log correctly
                     // FIXME: throw exception based on status like 429 Too Many Requests, etc.
                     response.failure = { resp, reader ->
                        println 'request failed'
                        println resp
                        println resp.statusLine
                        println resp.status
                        println reader.text

                        error = true
                     }
                  }

                  // create log to avoid syncing this resource again
                  if (!error)
                  {
                     log = new SyncLog(resourceType:'Account', resourceUid:account.uid, resourceLastUpdated:account.lastUpdated, remote: remote)
                     if (!log.save())
                     {
                        println log.errors
                        // TODO: handle errors or notify admins
                     }
                  }
                  else
                  {
                     error = false // reset error for next resource
                  }
               }
               catch (ConnectException)
               {
                  println "Remote not available "+ remote.remoteServerIP
               }
            }
         }

         // sync ehrs
         def ehrs = Ehr.findAllByMaster(true)

         ehrs.each { ehr ->

            logCount = SyncLog.countByResourceTypeAndResourceUidAndRemote('Ehr', ehr.uid, remote)

            if (logCount == 0)
            {
               def jb = new JsonBuilder()
               syncMarshallersService.toJSON(ehr, jb)

               // FIXME: ensure HTTPS!
               def http = new HTTPBuilder('http://'+ remote.remoteServerIP +':'+ remote.remoteServerPort)

               try
               {
                  http.request(POST) {
                     uri.path = remote.remoteServerPath + 'syncEhr'
                     uri.query = [:]
                     send JSON, jb.toString()
                     headers.Accept = 'application/json'
                     headers.Authorization = 'Bearer '+ remote.remoteAPIKey

                     response.success = { resp, json ->
                        println "POST Success: ${resp.statusLine}" // POST Success: HTTP/1.1 200 OK
                        //println resp.statusLine.statusCode // 200
                        //println json.getClass() // class net.sf.json.JSONArray

                        println json //.message
                     }

                     // FIXME: log correctly
                     // FIXME: throw exception based on status like 429 Too Many Requests, etc.
                     response.failure = { resp, reader ->
                        println 'request failed'
                        println resp
                        println resp.statusLine
                        println resp.status
                        println reader.text

                        error = true
                     }
                  }

                  // create log to avoid syncing this resource again
                  if (!error)
                  {
                     log = new SyncLog(resourceType:'Ehr', resourceUid:ehr.uid, resourceLastUpdated:ehr.lastUpdated, remote: remote)
                     if (!log.save())
                     {
                        println log.errors
                        // TODO: handle errors or notify admins
                     }
                  }
                  else
                  {
                     error = false // reset error for next resource
                  }
               }
               catch (ConnectException)
               {
                  println "Remote not available"
               }
            }
         }

         // sync OPTs
         def opts = OperationalTemplateIndex.findAllByMaster(true)

         opts.each { opt ->

            logCount = SyncLog.countByResourceTypeAndResourceUidAndRemote('OperationalTemplateIndex', opt.uid, remote)

            if (logCount == 0)
            {
               def jb = new JsonBuilder()
               syncMarshallersService.toJSON(opt, jb)

               // FIXME: ensure HTTPS!
               def http = new HTTPBuilder('http://'+ remote.remoteServerIP +':'+ remote.remoteServerPort)

               try
               {
                  http.request(POST) {
                     uri.path = remote.remoteServerPath + 'syncOpt'
                     uri.query = [:]
                     send JSON, jb.toString()
                     headers.Accept = 'application/json'
                     headers.Authorization = 'Bearer '+ remote.remoteAPIKey

                     response.success = { resp, json ->
                        println "POST Success: ${resp.statusLine}" // POST Success: HTTP/1.1 200 OK
                        //println resp.statusLine.statusCode // 200
                        //println json.getClass() // class net.sf.json.JSONArray

                        println json //.message
                     }

                     // FIXME: log correctly
                     // FIXME: throw exception based on status like 429 Too Many Requests, etc.
                     response.failure = { resp, reader ->
                        println 'request failed'
                        println resp
                        println resp.statusLine
                        println resp.status
                        println reader.text

                        error = true
                     }
                  }

                  // create log to avoid syncing this resource again
                  if (!error)
                  {
                     log = new SyncLog(resourceType:'OperationalTemplateIndex', resourceUid:opt.uid, resourceLastUpdated:opt.lastUpdated, remote: remote)
                     if (!log.save())
                     {
                        println log.errors
                        // TODO: handle errors or notify admins
                     }
                  }
                  else
                  {
                     error = false // reset error for next resource
                  }
               }
               catch (ConnectException)
               {
                  println "Remote not available"
               }
            }
         }

         // sync contributions
         def contributions = Contribution.findAllByMaster(true)

         contributions.each { contribution ->

            logCount = SyncLog.countByResourceTypeAndResourceUidAndRemote('Contribution', contribution.uid, remote)

            if (logCount == 0)
            {
               def jb = new JsonBuilder()
               syncMarshallersService.toJSON(contribution, jb)

               // FIXME: ensure HTTPS!
               def http = new HTTPBuilder('http://'+ remote.remoteServerIP +':'+ remote.remoteServerPort)

               try
               {
                  http.request(POST) {
                     uri.path = remote.remoteServerPath + 'syncContribution'
                     uri.query = [:]
                     send JSON, jb.toString()
                     headers.Accept = 'application/json'
                     headers.Authorization = 'Bearer '+ remote.remoteAPIKey

                     response.success = { resp, json ->
                        println "POST Success: ${resp.statusLine}" // POST Success: HTTP/1.1 200 OK
                        //println resp.statusLine.statusCode // 200
                        //println json.getClass() // class net.sf.json.JSONArray

                        println json //.message
                     }

                     // FIXME: log correctly
                     // FIXME: throw exception based on status like 429 Too Many Requests, etc.
                     response.failure = { resp, reader ->
                        println 'request failed'
                        println resp
                        println resp.statusLine
                        println resp.status
                        println reader.text

                        error = true
                     }
                  }

                  // create log to avoid syncing this resource again
                  if (!error)
                  {
                     log = new SyncLog(resourceType:'Contribution', resourceUid:contribution.uid, remote: remote)
                     if (!log.save())
                     {
                        println log.errors
                        // TODO: handle errors or notify admins
                     }
                  }
                  else
                  {
                     error = false // reset error for next resource
                  }
               }
               catch (ConnectException)
               {
                  println "Remote not available"
               }
            }
         }

         // sync queries
         def queries = Query.findAllByMaster(true)

         queries.each { query ->

            logCount = SyncLog.countByResourceTypeAndResourceUidAndRemote('Query', query.uid, remote)

            if (logCount == 0)
            {
               def jb = new JsonBuilder()
               syncMarshallersService.toJSON(query, jb)

               // FIXME: ensure HTTPS!
               def http = new HTTPBuilder('http://'+ remote.remoteServerIP +':'+ remote.remoteServerPort)

               try
               {
                  http.request(POST) {
                     uri.path = remote.remoteServerPath + 'syncQuery'
                     uri.query = [:]
                     send JSON, jb.toString()
                     headers.Accept = 'application/json'
                     headers.Authorization = 'Bearer '+ remote.remoteAPIKey

                     response.success = { resp, json ->
                        println "POST Success: ${resp.statusLine}" // POST Success: HTTP/1.1 200 OK
                        //println resp.statusLine.statusCode // 200
                        //println json.getClass() // class net.sf.json.JSONArray

                        println json //.message
                     }

                     // FIXME: log correctly
                     // FIXME: throw exception based on status like 429 Too Many Requests, etc.
                     response.failure = { resp, reader ->
                        println 'request failed'
                        println resp
                        println resp.statusLine
                        println resp.status
                        println reader.text

                        error = true
                     }
                  }

                  // create log to avoid syncing this resource again
                  if (!error)
                  {
                     log = new SyncLog(resourceType:'Query', resourceUid:query.uid, resourceLastUpdated:query.lastUpdated, remote: remote)
                     if (!log.save())
                     {
                        println log.errors
                        // TODO: handle errors or notify admins
                     }
                  }
                  else
                  {
                     error = false // reset error for next resource
                  }
               }
               catch (ConnectException)
               {
                  println "Remote not available"
               }
            }
         }


         // sync ehr queries
         def ehrqueries = EhrQuery.findAllByMaster(true)
         ehrqueries.each { equery ->

            logCount = SyncLog.countByResourceTypeAndResourceUidAndRemote('EhrQuery', equery.uid, remote)

            if (logCount == 0)
            {
               def jb = new JsonBuilder()
               syncMarshallersService.toJSON(equery, jb)

               // FIXME: ensure HTTPS!
               def http = new HTTPBuilder('http://'+ remote.remoteServerIP +':'+ remote.remoteServerPort)

               try
               {
                  http.request(POST) {
                     uri.path = remote.remoteServerPath + 'syncEhrQuery'
                     uri.query = [:]
                     send JSON, jb.toString()
                     headers.Accept = 'application/json'
                     headers.Authorization = 'Bearer '+ remote.remoteAPIKey

                     response.success = { resp, json ->
                        println "POST Success: ${resp.statusLine}" // POST Success: HTTP/1.1 200 OK
                        //println resp.statusLine.statusCode // 200
                        //println json.getClass() // class net.sf.json.JSONArray

                        println json //.message
                     }

                     // FIXME: log correctly
                     // FIXME: throw exception based on status like 429 Too Many Requests, etc.
                     response.failure = { resp, reader ->
                        println 'request failed'
                        println resp
                        println resp.statusLine
                        println resp.status
                        println reader.text

                        error = true
                     }
                  }

                  // create log to avoid syncing this resource again
                  if (!error)
                  {
                     log = new SyncLog(resourceType:'EhrQuery', resourceUid:equery.uid, resourceLastUpdated:equery.lastUpdated, remote: remote)
                     if (!log.save())
                     {
                        println log.errors
                        // TODO: handle errors or notify admins
                     }
                  }
                  else
                  {
                     error = false // reset error for next resource
                  }
               }
               catch (ConnectException)
               {
                  println "Remote not available"
               }
            }
         }

         // sync folders
         def folders = Folder.findAllByMaster(true)

         folders.each { folder ->

            logCount = SyncLog.countByResourceTypeAndResourceUidAndRemote('Folder', folder.uid, remote)

            if (logCount == 0)
            {
               def jb = new JsonBuilder()
               syncMarshallersService.toJSON(folder, jb)

               // FIXME: ensure HTTPS!
               def http = new HTTPBuilder('http://'+ remote.remoteServerIP +':'+ remote.remoteServerPort)

               try
               {
                  http.request(POST) {
                     uri.path = remote.remoteServerPath + 'syncFolder'
                     uri.query = [:]
                     send JSON, jb.toString()
                     headers.Accept = 'application/json'
                     headers.Authorization = 'Bearer '+ remote.remoteAPIKey

                     response.success = { resp, json ->
                        println "POST Success: ${resp.statusLine}" // POST Success: HTTP/1.1 200 OK
                        //println resp.statusLine.statusCode // 200
                        //println json.getClass() // class net.sf.json.JSONArray

                        println json //.message
                     }

                     // FIXME: log correctly
                     // FIXME: throw exception based on status like 429 Too Many Requests, etc.
                     response.failure = { resp, reader ->
                        println 'request failed'
                        println resp
                        println resp.statusLine
                        println resp.status
                        println reader.text

                        error = true
                     }
                  }

                  // create log to avoid syncing this resource again
                  if (!error)
                  {
                     log = new SyncLog(resourceType:'Folder', resourceUid:folder.uid, resourceLastUpdated:folder.lastUpdated, remote: remote)
                     if (!log.save())
                     {
                        println log.errors
                        // TODO: handle errors or notify admins
                     }
                  }
                  else
                  {
                     error = false // reset error for next resource
                  }
               }
               catch (ConnectException)
               {
                  println "Remote not available"
               }
            }
         }
      }
   }
}

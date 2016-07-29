/* Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.mortbay.log.Log;
import org.mortbay.util.StringUtil;
import org.mortbay.util.ajax.JSON;

import azkaban.executor.ExecutorApiClient;
import azkaban.trigger.TriggerManager;
import azkaban.trigger.TriggerManagerException;

/**
 * Helper class wrapping REST API client for Nyx Service
 *
 * @author gaggarwa
 *
 */
public class NyxUtils {
  private static Logger logger = Logger.getLogger(NyxUtils.class);
  public static final String NYX_SERVER_PORT = "nyx.service.port";
  public static final String NYX_SERVER_HOST = "nyx.service.host";

  private static String nyxServiceHost = "localhost";
  private static final boolean isHttp = true;
  private static int port = 8080;

  static {
    // populating nyx service from .properties configs
    Props props = TriggerManager.getAzprops();
    if (props != null) {
      nyxServiceHost = props.getString(NYX_SERVER_HOST, nyxServiceHost);
      port = props.getInt(NYX_SERVER_PORT, port);
    }
  }

  /**
   * Use trigger json specification to register a trigger with Nyx Service
   *
   * @throws TriggerManagerException
   */
  public static long registerNyxTrigger(String specificationJson)
      throws TriggerManagerException {
    try {
      ExecutorApiClient client = ExecutorApiClient.getInstance();
      List<NameValuePair> headers = new ArrayList<>();
      headers.add(new BasicNameValuePair("Content-Type", "application/json"));

      // if we passed the validation, go ahead and register the trigger.
      URI uri =
          ExecutorApiClient.buildUri(nyxServiceHost, port, "/register", isHttp);
      String rawResponse = client.httpPost(uri, headers, specificationJson);
      @SuppressWarnings("unchecked")
      Map<String, Object> parsedResponse =
          (Map<String, Object>) JSON.parse(rawResponse);

      // TODO: to be revisited. Presence of an "id" field signify
      // successfully
      // registration of trigger
      if (parsedResponse.containsKey("error")) {
        throw new IllegalArgumentException((String) parsedResponse.get("error"));
      } else if (parsedResponse.containsKey("id")) {
        return (Long) parsedResponse.get("id");
      } else {
        throw new TriggerManagerException("Failed to parse Nyx response "
            + rawResponse);
      }
    } catch (Exception ex) {
      logger.error("Failed to get Nyx service response for :"
          + specificationJson, ex);
      throw new TriggerManagerException(ex);
    }
  }

  public static void validateNyxTrigger(String specificationJson)
      throws TriggerManagerException {
    try {
      ExecutorApiClient client = ExecutorApiClient.getInstance();
      List<NameValuePair> headers = new ArrayList<>();
      headers.add(new BasicNameValuePair("Content-Type", "application/json"));
      URI uri =
          ExecutorApiClient.buildUri(nyxServiceHost, port, "/validate", isHttp);
      String rawResponse = client.httpPost(uri, headers, specificationJson);

      @SuppressWarnings("unchecked")
      Map<String, Object> parsedResponse =
          (Map<String, Object>) JSON.parse(rawResponse);

      // short cut if validation failed.
      if (!parsedResponse.containsKey("valid")
          || !parsedResponse.get("valid").toString().equalsIgnoreCase("true")) {
        String errorMsg = "";
        if (parsedResponse.get("errors") != null) {
          Object[] msgArr = (Object[]) parsedResponse.get("errors");
          for (int idx = 0; idx < msgArr.length; idx++) {
            errorMsg += (String) msgArr[idx];
          }
        } else {
          errorMsg = "NULL";
        }

        String err =
            String
                .format(
                    "Nyx trigger defination validation falied."
                        + " The execution can't be scheduled before the issue is fixed or the Nyx trigger is disabled."
                        + "\n Validation Error : \n %s ", errorMsg);
        logger.info(err);
        throw new TriggerManagerException(err);
      }
    } catch (Exception ex) {
      logger.error("Failed to get Nyx service response for :"
          + specificationJson, ex);
      throw new TriggerManagerException(ex);
    }
  }

  /**
   * Delete an already registered trigger from Nyx Service
   *
   * @throws TriggerManagerException
   */
  public static void unregisterNyxTrigger(Long triggerId)
      throws TriggerManagerException {
    if (triggerId == -1) {
      logger
          .info("skipping unregistering nyx trigger as the trigger is not yet registered with service.");
      return;
    }

    try {
      ExecutorApiClient client = ExecutorApiClient.getInstance();
      URI uri =
          ExecutorApiClient.buildUri(nyxServiceHost, port, "/unregister/"
              + triggerId, isHttp);
      String response = client.httpDelete(uri, null);

      @SuppressWarnings("unchecked")
      Map<String, Object> parsedResponse =
          (Map<String, Object>) JSON.parse(response);

      if (parsedResponse != null && parsedResponse.containsKey("error")) {
        throw new Exception((String) parsedResponse.get("error"));
      }
    } catch (Exception ex) {
      logger.error("Failed to get Nyx service response for triggerId : "
          + triggerId, ex);
      throw new TriggerManagerException(ex);
    }
  }

  /**
   * Look up status of an already registered trigger
   *
   * @param triggerId
   * @return status fetched from Nyx
   * @throws TriggerManagerException
   */
  public static boolean isNyxTriggerReady(Long triggerId)
      throws TriggerManagerException {
    if (triggerId == -1) {
      throw new TriggerManagerException("Trigger is not registered");
    }
    try {
      ExecutorApiClient client = ExecutorApiClient.getInstance();
      URI uri =
          ExecutorApiClient.buildUri(nyxServiceHost, port, "/status/"
              + triggerId, isHttp);

      String response = client.httpGet(uri, null);
      @SuppressWarnings("unchecked")
      Map<String, Object> parsedResponse =
          (Map<String, Object>) JSON.parse(response);
      if (parsedResponse.containsKey("error")) {
        throw new IllegalArgumentException((String) parsedResponse.get("error"));
      } else {
        if (parsedResponse.containsKey("ready")) {
          return (boolean) parsedResponse.get("ready");
        } else {
          throw new Exception("Status missing from Nyx response :" + response);
        }
      }
    } catch (Exception ex) {
      logger.error("Failed to get Nyx service response for triggerId : "
          + triggerId, ex);
      throw new TriggerManagerException(ex);
    }
  }

  /**
   * Look up status of an already registered trigger
   *
   * @param triggerId
   * @return status fetched from Nyx
   * @throws TriggerManagerException
   */
  public static boolean isNyxTriggerActive(Long triggerId)
      throws TriggerManagerException {
    if (triggerId == -1) {
      throw new TriggerManagerException("Trigger is not registered");
    }

    try {
      ExecutorApiClient client = ExecutorApiClient.getInstance();
      URI uri =
          ExecutorApiClient.buildUri(nyxServiceHost, port, "/status/"
              + triggerId, isHttp);
      String response = client.httpGet(uri, null);

      @SuppressWarnings("unchecked")
      Map<String, Object> parsedResponse =
          (Map<String, Object>) JSON.parse(response);

      if (parsedResponse.containsKey("error")) {
        throw new IllegalArgumentException((String) parsedResponse.get("error"));
      } else {
        if (parsedResponse.containsKey("active")) {
          return (boolean) parsedResponse.get("active");
        } else {
          throw new Exception("Status missing from Nyx response :" + response);
        }
      }
    } catch (Exception ex) {
      logger.error("Failed to get Nyx service response for triggerId : "
          + triggerId, ex);
      throw new TriggerManagerException(ex);
    }
  }

  /**
   * Fetches the trigger status details.
   *
   * @param triggerId
   * @return the detailed status in Map<String Object> type.
   * @throws TriggerManagerException
   * */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getNyxTriggerStatus(Long triggerId)
      throws TriggerManagerException {
    if (triggerId == -1) {
      throw new TriggerManagerException("Trigger is not registered");
    }

    try {
      ExecutorApiClient client = ExecutorApiClient.getInstance();
      URI uri =
          ExecutorApiClient.buildUri(nyxServiceHost, port, "/trigger/"
              + triggerId, isHttp);
      String response = client.httpGet(uri, null);
      return (Map<String, Object>) JSON.parse(response);
    } catch (Exception ex) {
      logger.error("Failed to get Nyx service response for triggerId : "
          + triggerId, ex);
      throw new TriggerManagerException(ex);
    }
  }
}

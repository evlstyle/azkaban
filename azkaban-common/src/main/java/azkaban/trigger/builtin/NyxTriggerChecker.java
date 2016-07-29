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

package azkaban.trigger.builtin;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import azkaban.trigger.ConditionChecker;
import azkaban.trigger.TriggerManagerException;
import azkaban.utils.NyxUtils;

/***
 * Trigger checker leveraging upcoming Nyx service
 *
 * @author gaggarwa
 *
 */
public class NyxTriggerChecker implements ConditionChecker {
  private static Logger logger = Logger.getLogger(NyxTriggerChecker.class);

  public static final String type = "NyxTriggerChecker";

  private String specification;
  private String id;
  private long triggerId = -1L;

  public NyxTriggerChecker(String specification, String id)
      throws TriggerManagerException {
    this(specification, id, -1);
  }

  public NyxTriggerChecker(String specification, String id, long triggerId)
      throws TriggerManagerException {
    this.specification = specification;
    this.id = id;

    // note :
    // when the checker is initialized we will take whatever that is passed
    // as the triggerId and will NOT attempt to register the trigger even if
    // id = -1, this is to make sure the trigger time is correctly populated
    // when user specifies a dynamic trigger time such as yesterDay().
    this.triggerId = triggerId;
    if (triggerId == -1) {
      NyxUtils.validateNyxTrigger(specification);
    }
  }

  public long getTriggerId() {
    return triggerId;
  }

  /**
   * Function to get the NYX trigger status
   *
   * @author evli
   *
   * @return the trigger status if the trigger is registered and status is
   *         successfully fetched from server.
   * */
  public Map<String, Object> getDetailedStatus() {
    Map<String, Object> returnVal = new HashMap<String, Object>();
    if (triggerId != -1) {
      try {
        returnVal = NyxUtils.getNyxTriggerStatus(triggerId);
      } catch (TriggerManagerException ex) {
        logger.error("Error while getting the detailed status for the trigger "
            + id, ex);
      }
    } else {
      logger.warn("attempted to retrieve status for an ungistered trigger.");
    }
    return returnVal;
  }

  @Override
  public Object eval() {
    try {
      if (triggerId == -1) {
        // if trigger is not registered then first register
        triggerId = NyxUtils.registerNyxTrigger(specification);
        logger
            .info("trigger successfully registered with Triggering service. Id = "
                + triggerId);
      }
      return NyxUtils.isNyxTriggerReady(triggerId);
    } catch (TriggerManagerException ex) {
      logger.error("Error while evaluating checker " + id, ex);
      return false;
    }
  }

  public boolean isTriggerDisabled() {
    try {
      if (triggerId == -1) {
        return false; // trigger is not yet registered so it is active.
      }

      return !NyxUtils.isNyxTriggerActive(triggerId);
    } catch (TriggerManagerException ex) {
      logger.error("Error while evaluating checker " + id, ex);
      return false;
    }
  }

  @Override
  public Object getNum() {
    return null;
  }

  @Override
  public void reset() {
    try {
      /**
       * Note - when resetting the trigger we simply go ahead and unregister it.
       * Once the trigger is validated again the trigger will be automatically
       * registered.
       * **/
      logger.info(String.format("Resetting triggerId = %s", triggerId));
      NyxUtils.unregisterNyxTrigger(triggerId);
      this.triggerId = -1;
    } catch (TriggerManagerException ex) {
      logger.error("Error while resetting checker " + id, ex);
    }
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getType() {
    return type;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ConditionChecker fromJson(Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  @Override
  public Object toJson() {
    Map<String, Object> jsonObj = new HashMap<String, Object>();
    jsonObj.put("type", type);
    jsonObj.put("specification", specification);
    jsonObj.put("triggerId", String.valueOf(triggerId));
    jsonObj.put("id", id);

    return jsonObj;
  }

  @Override
  public void stopChecker() {
    try {
      NyxUtils.unregisterNyxTrigger(triggerId);
    } catch (TriggerManagerException ex) {
      logger.error("Error while stopping checker " + id, ex);
    }
  }

  @Override
  public void setContext(Map<String, Object> context) {
    // Not applicable for Nyx trigger
  }

  @Override
  public long getNextCheckTime() {
    // Not applicable for Nyx trigger
    return Long.MAX_VALUE;
  }

  public static NyxTriggerChecker createFromJson(HashMap<String, Object> obj)
      throws Exception {
    Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    if (!jsonObj.get("type").equals(type)) {
      throw new Exception("Cannot create checker of " + type + " from "
          + jsonObj.get("type"));
    }
    Long triggerId = Long.valueOf((String) jsonObj.get("triggerId"));
    String id = (String) jsonObj.get("id");
    String specification = (String) jsonObj.get("specification");

    NyxTriggerChecker checker =
        new NyxTriggerChecker(specification, id, triggerId);
    return checker;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result =
        prime * result
            + ((specification == null) ? 0 : specification.hashCode());
    result = prime * result + (int) (triggerId ^ (triggerId >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NyxTriggerChecker other = (NyxTriggerChecker) obj;
    if (id == null) {
      if (other.id != null)
        return false;
    } else if (!id.equals(other.id))
      return false;
    if (specification == null) {
      if (other.specification != null)
        return false;
    } else if (!specification.equals(other.specification))
      return false;
    if (triggerId != other.triggerId)
      return false;
    return true;
  }

}

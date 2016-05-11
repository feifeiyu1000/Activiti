/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.activiti.engine.impl.persistence.entity;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.activiti.engine.impl.JobQueryImpl;
import org.activiti.engine.impl.Page;
import org.activiti.engine.impl.calendar.BusinessCalendar;
import org.activiti.engine.impl.calendar.CycleBusinessCalendar;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.persistence.entity.data.TimerJobDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tijs Rademakers
 * @author Vasile Dirla
 */
public class TimerJobEntityManagerImpl extends AbstractEntityManager<TimerJobEntity> implements TimerJobEntityManager {

  private static final Logger logger = LoggerFactory.getLogger(TimerJobEntityManagerImpl.class);

  protected TimerJobDataManager jobDataManager;

  public TimerJobEntityManagerImpl(ProcessEngineConfigurationImpl processEngineConfiguration, TimerJobDataManager jobDataManager) {
    super(processEngineConfiguration);
    this.jobDataManager = jobDataManager;
  }

  @Override
  public List<TimerJobEntity> findJobsByQueryCriteria(JobQueryImpl jobQuery, Page page) {
    return jobDataManager.findJobsByQueryCriteria(jobQuery, page);
  }
  
  @Override
  public long findJobCountByQueryCriteria(JobQueryImpl jobQuery) {
    return jobDataManager.findJobCountByQueryCriteria(jobQuery);
  }

  @Override
  public TimerJobEntity createTimer(JobEntity te) {
    TimerJobEntity newTimerEntity = create();
    newTimerEntity.setJobHandlerConfiguration(te.getJobHandlerConfiguration());
    newTimerEntity.setJobHandlerType(te.getJobHandlerType());
    newTimerEntity.setExclusive(te.isExclusive());
    newTimerEntity.setRepeat(te.getRepeat());
    newTimerEntity.setRetries(te.getRetries());
    newTimerEntity.setEndDate(te.getEndDate());
    newTimerEntity.setExecutionId(te.getExecutionId());
    newTimerEntity.setProcessInstanceId(te.getProcessInstanceId());
    newTimerEntity.setProcessDefinitionId(te.getProcessDefinitionId());

    // Inherit tenant
    newTimerEntity.setTenantId(te.getTenantId());
    newTimerEntity.setJobType(JobEntity.JOB_TYPE_TIMER);
    return newTimerEntity;
  }
  
  @Override
  public void createAndCalculateNextTimer(JobEntity timerEntity) {
    int repeatValue = calculateRepeatValue(timerEntity);
    if (repeatValue != 0) {
      if (repeatValue > 0) {
        setNewRepeat(timerEntity, repeatValue);
      }
      Date newTimer = calculateNextTimer(timerEntity);
      if (newTimer != null && isValidTime(timerEntity, newTimer)) {
        TimerJobEntity te = createTimer(timerEntity);
        te.setDuedate(newTimer);
        insert(te);
      }
    }
  }
  
  protected void setNewRepeat(JobEntity timerEntity, int newRepeatValue) {
    List<String> expression = Arrays.asList(timerEntity.getRepeat().split("/"));
    expression = expression.subList(1, expression.size());
    StringBuilder repeatBuilder = new StringBuilder("R");
    repeatBuilder.append(newRepeatValue);
    for (String value : expression) {
      repeatBuilder.append("/");
      repeatBuilder.append(value);
    }
    timerEntity.setRepeat(repeatBuilder.toString());
  }
  
  protected boolean isValidTime(JobEntity timerEntity, Date newTimerDate) {
    BusinessCalendar businessCalendar = getProcessEngineConfiguration().getBusinessCalendarManager().getBusinessCalendar(CycleBusinessCalendar.NAME);
    return businessCalendar.validateDuedate(timerEntity.getRepeat(), timerEntity.getMaxIterations(), timerEntity.getEndDate(), newTimerDate);
  }
  
  protected Date calculateNextTimer(JobEntity timerEntity) {
    BusinessCalendar businessCalendar = getProcessEngineConfiguration().getBusinessCalendarManager().getBusinessCalendar(CycleBusinessCalendar.NAME);
    return businessCalendar.resolveDuedate(timerEntity.getRepeat(), timerEntity.getMaxIterations());
  }

  @Override
  public List<TimerJobEntity> selectTimerJobsToDueDate(Page page) {
    return jobDataManager.selectTimerJobsToDueDate(page);
  }

  @Override
  public List<TimerJobEntity> findJobsByTypeAndProcessDefinitionId(String jobHandlerType, String processDefinitionId) {
    return jobDataManager.findJobsByTypeAndProcessDefinitionId(jobHandlerType, processDefinitionId);
  }
  
  @Override
  public Collection<TimerJobEntity> findJobsByExecutionId(String id) {
    return jobDataManager.findJobsByExecutionId(id);
  }

  @Override
  public void insert(TimerJobEntity jobEntity, boolean fireCreateEvent) {

    // add link to execution
    if (jobEntity.getExecutionId() != null) {
      ExecutionEntity execution = getExecutionEntityManager().findById(jobEntity.getExecutionId());
      execution.getJobs().add(jobEntity);

      // Inherit tenant if (if applicable)
      if (execution.getTenantId() != null) {
        jobEntity.setTenantId(execution.getTenantId());
      }
    }

    super.insert(jobEntity, fireCreateEvent);
  }

  @Override
  public void insert(TimerJobEntity jobEntity) {
    insert(jobEntity, true);
  }

  protected int calculateRepeatValue(JobEntity timerEntity) {
    int times = -1;
    List<String> expression = Arrays.asList(timerEntity.getRepeat().split("/"));
    if (expression.size() > 1 && expression.get(0).startsWith("R") && expression.get(0).length() > 1) {
      times = Integer.parseInt(expression.get(0).substring(1));
      if (times > 0) {
        times--;
      }
    }
    return times;
  }
  
  protected TimerJobDataManager getDataManager() {
    return jobDataManager;
  }

  public void setJobDataManager(TimerJobDataManager jobDataManager) {
    this.jobDataManager = jobDataManager;
  }
}

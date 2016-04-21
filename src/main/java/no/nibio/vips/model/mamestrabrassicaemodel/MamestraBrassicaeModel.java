/*
 * Copyright (c) 2016 NIBIO <http://www.nibio.no/>. 
 * 
 * This file is part of MamestraBrassicaeModel.
 * MamestraBrassicaeModel is free software: you can redistribute it and/or modify
 * it under the terms of the NIBIO Open Source License as published by 
 * NIBIO, either version 1 of the License, or (at your option) any
 * later version.
 * 
 * MamestraBrassicaeModel is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * NIBIO Open Source License for more details.
 * 
 * You should have received a copy of the NIBIO Open Source License
 * along with MamestraBrassicaeModel.  If not, see <http://www.nibio.no/licenses/>.
 * 
 */

package no.nibio.vips.model.mamestrabrassicaemodel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import no.nibio.vips.entity.ModelConfiguration;
import no.nibio.vips.entity.Result;
import no.nibio.vips.entity.ResultImpl;
import no.nibio.vips.entity.WeatherObservation;
import no.nibio.vips.i18n.I18nImpl;
import no.nibio.vips.model.ConfigValidationException;
import no.nibio.vips.model.Model;
import no.nibio.vips.model.ModelExcecutionException;
import no.nibio.vips.model.ModelId;
import no.nibio.vips.util.CommonNamespaces;
import no.nibio.vips.util.InvalidAggregationTypeException;
import no.nibio.vips.util.ModelUtil;
import no.nibio.vips.util.WeatherElements;
import no.nibio.vips.util.WeatherObservationListException;
import no.nibio.vips.util.WeatherUtil;

/**
 * Forecasting model for Mamestra brassicae (NO: Kålfly)
 * @copyright 2016 <a href="http://www.nibio.no/">NIBIO</a>
 * @author Tor-Einar Skog <tor-einar.skog@nibio.no>
 */
public class MamestraBrassicaeModel extends I18nImpl implements Model{
    public final static ModelId MODEL_ID = new ModelId("MAMESTRABR");
    private final ModelUtil modelUtil;
    
    // Model constants
    private final static Integer THRESHOLD_TMDD_PUPAE = 129;
    private final static Integer THRESHOLD_TMDD_EGG_DEPOSIT = 56;
    private final static Integer THRESHOLD_TMDD_EGG_HATCH = 75;
    private final static Integer THRESHOLD_TMDD_LARVAE_1 = 66;
    private final static Integer THRESHOLD_TMDD_LARVAE_2 = 49;
    private final static Integer THRESHOLD_TMDD_LARVAE_3 = 53;
    private final static Integer THRESHOLD_TMDD_LARVAE_4 = 67;
    private final static Integer THRESHOLD_TMDD_LARVAE_5 = 62;
    private final static Double T0_PUPAE = 7.5; // Day degree base temp phase 1
    private final static Double T0_EGG_DEPOSIT = 5.0; // Day degree base temp phase 2
    private final static Double T0_EGG_HATCH = 8.6; // Day degree base temp phase 3a
    private final static Double T0_L1 = 7.7; // Day degree base temp phase 3b
    private final static Double T0_L2 = 6.9; // Day degree base temp phase 4a
    private final static Double T0_L3 = 6.3; // Day degree base temp phase 4b
    private final static Double T0_L4 = 4.3; // Day degree base temp phase 5a
    private final static Double T0_L5 = 6.0; // Day degree base temp phase 5b
    
    // Collections
    private List<WeatherObservation> TJM10;
    private List<WeatherObservation> TM;
    private DataMatrix dataMatrix;
    
    // Other globals
    private TimeZone timeZone;

    public MamestraBrassicaeModel()
    {
        super("no.nibio.vips.model.mamestrabrassicaemodel.texts");
        this.modelUtil = new ModelUtil();
    }
    
    @Override
    public List<Result> getResult() throws ModelExcecutionException {
        
        Double accumulatedDayDegrees = 0.0;
        Date currentDate = this.dataMatrix.getFirstDateWithParameterValue(DataMatrix.TJM10D);
        Date endDate = this.dataMatrix.getLastDateWithParameterValue(DataMatrix.TMD);
        DecimalFormat dFormat = new DecimalFormat("###.##");
        List<Result> retVal = new ArrayList<>();
        Calendar cal = Calendar.getInstance(timeZone);
        while(currentDate.before(endDate))
        {
            Double baseTemp = this.findT0(accumulatedDayDegrees);
            Double todayTemp = accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE ? 
                    ((WeatherObservation)this.dataMatrix.getParamValueForDate(currentDate, DataMatrix.TJM10D)).getValue()
                    : ((WeatherObservation)this.dataMatrix.getParamValueForDate(currentDate, DataMatrix.TMD)).getValue();
           
            accumulatedDayDegrees += Math.max(0, todayTemp - baseTemp);
            
            
            
            Result result = new ResultImpl();
            result.setResultValidTime(currentDate);
            result.setWarningStatus(this.getWarningStatus(accumulatedDayDegrees));
            this.setAllThresholds(result);
            
            result.setValue(this.getModelId().toString(),"TMDD", dFormat.format(accumulatedDayDegrees));
            
            // Set input data
            result.setValue(CommonNamespaces.NS_WEATHER, "TJM10D", dFormat.format(((WeatherObservation)this.dataMatrix.getParamValueForDate(currentDate, DataMatrix.TJM10D)).getValue()));
            result.setValue(CommonNamespaces.NS_WEATHER, "TMD", dFormat.format(((WeatherObservation)this.dataMatrix.getParamValueForDate(currentDate, DataMatrix.TMD)).getValue()));
            
            retVal.add(result);
            cal.setTime(currentDate);
            cal.add(Calendar.DATE, 1);
            currentDate = cal.getTime();
        }
        return retVal;
    }

    @Override
    public ModelId getModelId() {
        return MamestraBrassicaeModel.MODEL_ID;
    }

    @Override
    public String getModelName() {
        return this.getModelName(Model.DEFAULT_LANGUAGE);
    }

    @Override
    public String getModelName(String language) {
        return this.getText("name", language);
    }

    @Override
    public String getLicense() {
        return "Copyright (c) 2016 NIBIO <http://www.nibio.no/>. \n" +
            "\n" +
            "This file is part of LygusRugulipennisModel. \n" + 
            "MamestraBrassicaeModel is free software: you can redistribute it and/or modify \n" + 
            "it under the terms of the NIBIO Open Source License as published by \n" + 
            "NIBIO, either version 1 of the License, or (at your option) any \n" +
            "later version. \n" +
            "\n" +
            "MamestraBrassicaeModel is distributed in the hope that it will be useful, \n" +
            "but WITHOUT ANY WARRANTY; without even the implied warranty of \n" +
            "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the \n" +
            "NIBIO Open Source License for more details. \n" +
            "" +
            "You should have received a copy of the NIBIO Open Source License \n" +
            "along with MamestraBrassicaeModel.  If not, see <http://www.nibio.no/licenses/>. \n";
    }

    @Override
    public String getCopyright() {
        return "(c) 2016 NIBIO (http://www.nibio.no/). Contact: post@nibio.no";
    }

    @Override
    public String getModelDescription() {
        return this.getModelDescription(Model.DEFAULT_LANGUAGE);
    }

    @Override
    public String getModelDescription(String language) {
        try
        {
            return this.modelUtil.getTextWithBase64EncodedImages(this.getText("description", language), this.getClass());
        }
        catch(IOException ex)
        {
            return this.getText("description", language);
        }
    }

    @Override
    public String getWarningStatusInterpretation() {
        return this.getWarningStatusInterpretation(Model.DEFAULT_LANGUAGE);
    }

    @Override
    public String getWarningStatusInterpretation(String language) {
        return this.getText("statusInterpretation", language);
    }

    @Override
    public String getModelUsage() {
        return this.getModelUsage(Model.DEFAULT_LANGUAGE);
    }

    @Override
    public String getModelUsage(String language) {
        return this.getText("usage", language);
    }

    @Override
    public String getSampleConfig() {
        return  "{\n" +
                "\t\"loginInfo\":{\n" +
                "\t\t\"username\":\"example\",\n" +
                "\t\t\"password\":\"example\"\n" +
                "\t},\n" +
                "\t\"modelId\":\"" + MODEL_ID.toString() + "\",\n" +
                "\t\"configParameters\":{\n" +
                "\t\t\"observations\":[\n" +
                "\t\t{\n" +
                "\t\t\t\t\"timeMeasured\": \"2015-01-01T00:00:00+02:00\",\n" +
                "\t\t\t\t\"elementMeasurementTypeId\":\"TM\",\n" +
                "\t\t\t\t\"logIntervalId\":2,\n" +
                "\t\t\t\t\"value\":1.1\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\t\"timeMeasured\": \"2015-01-01T00:00:00+02:00\",\n" +
                "\t\t\t\t\"elementMeasurementTypeId\":\"TJM10\",\n" +
                "\t\t\t\t\"logIntervalId\":2,\n" +
                "\t\t\t\t\"value\":0.9\n" +
                "\t\t}\n" +
                "\t\t]\n" +
                "\t}\n" +
                "}\n";
    }

    @Override
    public void setConfiguration(ModelConfiguration config) throws ConfigValidationException {
        // Initialize the weather data collections
        this.TM = new ArrayList<>();
        this.TJM10 = new ArrayList<>();
        // Init data matrix
        this.dataMatrix = new DataMatrix();
        
        ObjectMapper mapper = new ObjectMapper();
        
        // Setting timezone
        this.timeZone = TimeZone.getTimeZone((String) config.getConfigParameter("timeZone"));
        //System.out.println("TimeZone=" + this.timeZone);
        
        // Importing weather data, creating collections
        // Can accept both hourly and daily data
        WeatherUtil wUtil = new WeatherUtil();
        List<WeatherObservation> observations = mapper.convertValue(config.getConfigParameter("observations"), new TypeReference<List<WeatherObservation>>(){});
        for(WeatherObservation o:observations)
        {
            switch(o.getElementMeasurementTypeId())
            {
                case WeatherElements.TEMPERATURE_MEAN:
                    if(o.getLogIntervalId().equals(WeatherObservation.LOG_INTERVAL_ID_1H))
                    {
                        this.TM.add(o);
                    }else {
                        o.setTimeMeasured(wUtil.pragmaticAdjustmentToMidnight(o.getTimeMeasured(), timeZone));
                        this.dataMatrix.setParamValueForDate(o.getTimeMeasured(), DataMatrix.TMD, o);
                    }break;
                case WeatherElements.SOIL_TEMPERATURE_10CM_MEAN:
                    if(o.getLogIntervalId().equals(WeatherObservation.LOG_INTERVAL_ID_1H))
                    {
                        this.TJM10.add(o);
                    }else {
                        o.setTimeMeasured(wUtil.pragmaticAdjustmentToMidnight(o.getTimeMeasured(), timeZone));
                        this.dataMatrix.setParamValueForDate(o.getTimeMeasured(), DataMatrix.TJM10D, o);
                    }break;
                default:
                    // Keep calm and continue importing data
                    break;
            }
        }
        
        // If we've received hourly weather data, create and store daily values
        // Air temperature
        if(dataMatrix.getFirstDateWithParameterValue(DataMatrix.TMD) == null)
        {
            try {
                List<WeatherObservation> dailyTemperatures = new WeatherUtil().getAggregatedDailyValues(
                        this.TM,
                        this.timeZone,
                        15,
                        WeatherUtil.AGGREGATION_TYPE_AVERAGE);
                for(WeatherObservation obs:dailyTemperatures)
                {
                    this.dataMatrix.setParamValueForDate(obs.getTimeMeasured(), DataMatrix.TMD, obs);
                }
            } catch (WeatherObservationListException | InvalidAggregationTypeException ex) {
                throw new ConfigValidationException(ex.getMessage());
            }
        }
        // Soil temperature
        if(dataMatrix.getFirstDateWithParameterValue(DataMatrix.TJM10D) == null)
        {
            try {
                List<WeatherObservation> dailyTemperatures = new WeatherUtil().getAggregatedDailyValues(
                        this.TJM10,
                        this.timeZone,
                        15,
                        WeatherUtil.AGGREGATION_TYPE_AVERAGE);
                if(dailyTemperatures != null)
                {
                    for(WeatherObservation obs:dailyTemperatures)
                    {
                        this.dataMatrix.setParamValueForDate(obs.getTimeMeasured(), DataMatrix.TJM10D, obs);
                    }
                }
            } catch (WeatherObservationListException | InvalidAggregationTypeException ex) {
                throw new ConfigValidationException(ex.getMessage());
            }
        }
        
        if(this.dataMatrix.getFirstDateWithParameterValue(DataMatrix.TJM10D)
                .after(this.dataMatrix.getLastDateWithParameterValue(DataMatrix.TMD))
                )
        {
            throw new ConfigValidationException("Error with weather data. First day with soil temp = " + 
                    this.dataMatrix.getFirstDateWithParameterValue(DataMatrix.TJM10D) + ", last day with air temp = " + 
                    this.dataMatrix.getLastDateWithParameterValue(DataMatrix.TMD));
        }
        
    }

    private Double findT0(Double accumulatedDayDegrees)
    {
      if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE) return T0_PUPAE;
      else if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE + THRESHOLD_TMDD_EGG_DEPOSIT) return T0_EGG_DEPOSIT;
      else if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE + THRESHOLD_TMDD_EGG_DEPOSIT 
              + THRESHOLD_TMDD_EGG_HATCH) return T0_EGG_HATCH;
      else if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE + THRESHOLD_TMDD_EGG_DEPOSIT 
              + THRESHOLD_TMDD_EGG_HATCH + THRESHOLD_TMDD_LARVAE_1) return T0_L1;
      else if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE + THRESHOLD_TMDD_EGG_DEPOSIT 
              + THRESHOLD_TMDD_EGG_HATCH + THRESHOLD_TMDD_LARVAE_1 + THRESHOLD_TMDD_LARVAE_2) return T0_L2;
      else if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE + THRESHOLD_TMDD_EGG_DEPOSIT 
              + THRESHOLD_TMDD_EGG_HATCH + THRESHOLD_TMDD_LARVAE_1 + THRESHOLD_TMDD_LARVAE_2
              + THRESHOLD_TMDD_LARVAE_3) return T0_L3;
      else if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE + THRESHOLD_TMDD_EGG_DEPOSIT 
              + THRESHOLD_TMDD_EGG_HATCH + THRESHOLD_TMDD_LARVAE_1 + THRESHOLD_TMDD_LARVAE_2
              + THRESHOLD_TMDD_LARVAE_3 + THRESHOLD_TMDD_LARVAE_4) return T0_L4;
      else if(accumulatedDayDegrees < THRESHOLD_TMDD_LARVAE_1 + THRESHOLD_TMDD_LARVAE_2
              + THRESHOLD_TMDD_LARVAE_3 + THRESHOLD_TMDD_LARVAE_4 + THRESHOLD_TMDD_LARVAE_5) return T0_L5;
      else return T0_L5; 
    }

    private Integer getWarningStatus(Double accumulatedDayDegrees) {
        if(accumulatedDayDegrees < THRESHOLD_TMDD_PUPAE)
        {
            return Result.WARNING_STATUS_NO_RISK;
        }
        else if(accumulatedDayDegrees < 
                THRESHOLD_TMDD_PUPAE 
                + THRESHOLD_TMDD_EGG_DEPOSIT
                )
        {
            return Result.WARNING_STATUS_MINOR_RISK;
        }
        else if(accumulatedDayDegrees < 
                THRESHOLD_TMDD_PUPAE + 
                THRESHOLD_TMDD_EGG_DEPOSIT + 
                THRESHOLD_TMDD_EGG_HATCH +
                THRESHOLD_TMDD_LARVAE_1 +
                THRESHOLD_TMDD_LARVAE_2 +
                THRESHOLD_TMDD_LARVAE_3 +
                THRESHOLD_TMDD_LARVAE_4 + 
                THRESHOLD_TMDD_LARVAE_5
                )
        {
            return Result.WARNING_STATUS_HIGH_RISK;
        }
        else
        {
            return Result.WARNING_STATUS_NO_WARNING;
        }
    }

    private void setAllThresholds(Result result) {
        result.setValue(this.getModelId().toString(), "SWARMING_START", String.valueOf(THRESHOLD_TMDD_PUPAE));
        result.setValue(this.getModelId().toString(), "EGG_HATCH_START", String.valueOf(THRESHOLD_TMDD_PUPAE + THRESHOLD_TMDD_EGG_DEPOSIT));
        result.setValue(this.getModelId().toString(), "TREATMENT_OPTIMUM", String.valueOf(
                THRESHOLD_TMDD_PUPAE 
                + THRESHOLD_TMDD_EGG_DEPOSIT
                + THRESHOLD_TMDD_EGG_HATCH
                + THRESHOLD_TMDD_LARVAE_1
            )
        );
        
        result.setValue(this.getModelId().toString(), "TREATMENT_LATE", String.valueOf(
                THRESHOLD_TMDD_PUPAE 
                + THRESHOLD_TMDD_EGG_DEPOSIT
                + THRESHOLD_TMDD_EGG_HATCH
                + THRESHOLD_TMDD_LARVAE_1
                + THRESHOLD_TMDD_LARVAE_2
                + THRESHOLD_TMDD_LARVAE_3
            )
        );
    }
}

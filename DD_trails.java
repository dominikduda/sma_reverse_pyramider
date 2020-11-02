package jforex;

import com.dukascopy.api.indicators.DoubleRangeDescription;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.IIndicatorContext;
import com.dukascopy.api.indicators.IIndicatorsProvider;
import com.dukascopy.api.indicators.IndicatorInfo;
import com.dukascopy.api.indicators.IndicatorResult;
import com.dukascopy.api.indicators.InputParameterInfo;
import com.dukascopy.api.indicators.IntegerListDescription;
import com.dukascopy.api.indicators.IntegerRangeDescription;
import com.dukascopy.api.indicators.OptInputParameterInfo;
import com.dukascopy.api.indicators.OutputParameterInfo;

import com.dukascopy.api.IConsole;

import java.awt.Color;

public class DD_trails implements IIndicator{
    private IndicatorInfo indicatorInfo;
    private InputParameterInfo[] inputParameterInfos;
    private OptInputParameterInfo[] optInputParameterInfos;
    private OutputParameterInfo[] outputParameterInfos;
    //Price includes 5 arrays: open, close, high, low, volume
    private double[][] inputsPriceArr = new double[4][]; 
    //price array depending on AppliedPrice
    private double[][] inputsDouble = new double[1][]; 
    private int atrTimePeriod = 55;
    private double atrMultiplier = 2;
    private double[][] outputs = new double[2][];
    private IIndicatorsProvider indicatorsProvider;
    private IIndicator atrIndicator;
    private IConsole console;
    private int appliedPrice = CLOSE;
        
    public static final int OPEN = 0;
    public static final int CLOSE = 1;
    public static final int HIGH = 2;
    public static final int LOW = 3;
       
    public void onStart(IIndicatorContext context) {
        console = context.getConsole();
        indicatorsProvider = context.getIndicatorsProvider();
        atrIndicator = indicatorsProvider.getIndicator("ATR");
        indicatorInfo = new IndicatorInfo("DD_trails", "DD_ATR", "My indicators", 
              true, false, false,
              2, 2, 2);
        
        int[] priceValues = {OPEN, HIGH, LOW, CLOSE};
        String[] priceNames = {"Open", "High", "Low", "Close"};
     
        inputParameterInfos = new InputParameterInfo[] {
                new InputParameterInfo("atr Input", InputParameterInfo.Type.PRICE),
                new InputParameterInfo("atr Input 1", InputParameterInfo.Type.DOUBLE)};
                
        optInputParameterInfos = new OptInputParameterInfo[] {
                new OptInputParameterInfo("ATR Time period", 
                    OptInputParameterInfo.Type.OTHER, 
                    new IntegerRangeDescription(atrTimePeriod, 2, 100, 1)),
                new OptInputParameterInfo("ATR Multiplier", 
                    OptInputParameterInfo.Type.OTHER, 
                    new DoubleRangeDescription(atrMultiplier, 0.001, 100.0, 0.1, 3))};
                    
        outputParameterInfos = new OutputParameterInfo[] {
                new OutputParameterInfo("TopBand", 
                    OutputParameterInfo.Type.DOUBLE, 
                    OutputParameterInfo.DrawingStyle.LINE){
                {
                   setColor(Color.RED);
                }
            },
                new OutputParameterInfo("BottomBand", 
                    OutputParameterInfo.Type.DOUBLE, 
                    OutputParameterInfo.DrawingStyle.LINE){
                {
                   setColor(Color.BLUE);
                }
            }};
            //indicatorInfo.setRecalculateOnNewCandleOnly(true);
            
    }

    public IndicatorResult calculate(int startIndex, int endIndex) {      
       if (startIndex - getLookback() < 0) {
           startIndex -= startIndex - getLookback();
       }
              
       int len = endIndex - startIndex + 1;
       double[] atr = new double[len];
       atrIndicator.setInputParameter(0, inputsPriceArr);
       atrIndicator.setOutputParameter(0, atr);
       atrIndicator.calculate(startIndex, endIndex);
                     
       int i,j = 1;
       
       try
       {              
        outputs[0][0] = inputsPriceArr[HIGH][startIndex] + atrMultiplier*atr[0]; 
        outputs[1][0] = inputsPriceArr[LOW][startIndex] - atrMultiplier*atr[0];

        
        for ( i = (startIndex + 1), j = 1; i <= endIndex; i++, j++) 
        {
            
            outputs[0][j] = inputsPriceArr[HIGH][i] + atrMultiplier*atr[j];
            
            if(outputs[0][j - 1] <= inputsPriceArr[HIGH][i - 1])
             continue;
            if(outputs[0][j - 1] <= outputs[0][j])
            {
             outputs[0][j] = outputs[0][j - 1];
             continue;
            }
        }


        //this.console.getOut().println("Top i = " + Integer.toString(i) + " j = " + Integer.toString(j));

        for ( i = (startIndex + 1), j = 1; i <= endIndex; i++, j++) 
        {
            
            outputs[1][j] = inputsPriceArr[LOW][i] - atrMultiplier*atr[j];
            
            if(outputs[1][j - 1] >= inputsPriceArr[LOW][i - 1])
             continue;
            if(outputs[1][j - 1] >= outputs[1][j])
            {
             outputs[1][j] = outputs[1][j - 1];
             continue;
            }
        }
        
       
       }
       catch (Exception e) 
       {            
              this.console.getErr().println(e.getMessage());
       };                   
       
                                         
       return new IndicatorResult(startIndex, j);
    }

    public IndicatorInfo getIndicatorInfo() {
        return indicatorInfo;
    }

    public InputParameterInfo getInputParameterInfo(int index) {
        //this.console.getOut().println("getInputParameterInfo - index = " + Integer.toString(index));
        if (index <= inputParameterInfos.length) {
            return inputParameterInfos[index];
        }
        return null;
    }

    public int getLookback() {
        return atrIndicator.getLookback();
    }

    public int getLookforward() {
        return 0;
    }

    public OptInputParameterInfo getOptInputParameterInfo(int index) {
        if (index <= optInputParameterInfos.length) {
            return optInputParameterInfos[index];
        }
        return null;
    }

    public OutputParameterInfo getOutputParameterInfo(int index) {
        if (index <= outputParameterInfos.length) {
            return outputParameterInfos[index];
        }
        return null;
    }

    public void setInputParameter(int index, Object array) {
        if(index == 0)
            inputsPriceArr = (double[][]) array;
        else if(index == 1)
            inputsDouble[0] = (double[]) array;    }

    public void setOptInputParameter(int index, Object value) {
         switch (index) {                
            case 0:
             atrTimePeriod = (Integer) value;
             atrIndicator.setOptInputParameter(0, (Integer) value);        
             break;                    
         case 1:
             atrMultiplier = (Double) value;         
             break;                    
         default:
             throw new ArrayIndexOutOfBoundsException(index);
         }
    }

    public void setOutputParameter(int index, Object array) {
        outputs[index] = (double[]) array;
    }
}
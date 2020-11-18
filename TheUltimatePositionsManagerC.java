package jforex;

import java.util.*;
import java.text.*;

import com.dukascopy.api.*;
import java.math.*;
import java.util.HashSet;
import java.util.Set;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.util.TimePeriodAggregationFeedDescriptor;

public class TheUltimatePositionsManagerC implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;

    private int ordersCounter;
    private int entriesClosedByRiskManagement = 0;
    private double prevBandCached = -1;
    private boolean volatileBarDetected = false;

    private double closeLimitLevelForStopLoss = -1;
    private double closeLimitLevel = -1;
    private boolean firstOrderTriggered = false;

    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;
    @Configurable(value="Strating close limit level", stepSize=0.001)
    public double startingCloseLimitLevel = -1;
    @Configurable(value="Assume first order triggered", stepSize=0.001)
    public boolean startingFirstOrderTriggerred = false;
    @Configurable(value="Minimum extremes distance for stop loss jump pips", stepSize=1)
    public int minimumExtremesDistanceForStopLossJumpPips = 10;
    @Configurable(value="Trailing stops pips", stepSize=1)
    public int trailingStopsPips = 80;
    @Configurable(value="Volatile bar pips", stepSize=1)
    public int volatileBarPips = 120;
    @Configurable(value="Starting prev band value", stepSize=0.001)
    public double startingPrevBandValue = -1;
    @Configurable(value="------------------------", stepSize=0.001)
    public boolean xxx = false;
    @Configurable("Time Frame - DD_trails")
    public Period slowTrailsTimeFrame = Period.FOUR_HOURS;
    @Configurable("Time period - DD_trails")
    public int slowTrailsLookback = 6;
    @Configurable(value="ATR multiplier - DD_trails", stepSize=0.001)
    public double slowTrailsMultiplier = 0.2;
    @Configurable("Time Frame - Fast DD_trails")
    public Period fastTrailsTimeFrame = Period.ONE_MIN;
    @Configurable("Time period - Fast DD_trails")
    public int fastTrailsLookback = 55;
    @Configurable(value="ATR multiplier - Fast DD_trails", stepSize=0.001)
    public double fastTrailsMultiplier = 10.0;
    @Configurable(value="Slippage", stepSize=0.001)
    public double Slippage = 3.0;

    private IOrder order = null;


    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        this.ordersCounter = 0;
        this.prevBandCached = startingPrevBandValue;
        this.closeLimitLevel = startingCloseLimitLevel;
        this.firstOrderTriggered = startingFirstOrderTriggerred;
        // Do subscribe selected instrument
        Set subscribedInstruments = new HashSet();
        subscribedInstruments.add(this.instrument);
        context.setSubscribedInstruments(subscribedInstruments);
        setTrailingStopsToAllOrders();
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        // Print messages, but related to own orders
        if (message.getOrder() != null) {
            if (message.getOrder().getInstrument() != this.instrument) {
              return;
            }
            String orderLabel = message.getOrder().getLabel();
            IMessage.Type messageType = message.getType();
            switch (messageType) {
                case ORDER_FILL_OK:
                    // debug if (because usually initial order is made before script is ran)
                    // if (filledOrdersCount() >= 1) {
                      this.firstOrderTriggered = true;
                    // }
                    IBar lastBar = getBar(slowTrailsTimeFrame, stopLossOfferSide(), 1);
                    if (isLong() && this.prevBandCached > lastBar.getLow() || !isLong() && this.prevBandCached < lastBar.getHigh()) {
                      if (isLong()) {
                        if (this.closeLimitLevel == -1 || this.closeLimitLevel < lastBar.getLow()) {
                          moveAllStopLossesToCachedLevel();
                          cacheCloseLimitLevelForStopLoss();
                          this.closeLimitLevel = lastBar.getLow();
                        }
                      } else {
                        if (this.closeLimitLevel == -1 || this.closeLimitLevel > lastBar.getHigh()) {
                          moveAllStopLossesToCachedLevel();
                          cacheCloseLimitLevelForStopLoss();
                          this.closeLimitLevel = lastBar.getHigh();
                        }
                      }
                    }
                    break;
                case ORDER_CHANGED_OK:
                    break;
                case ORDER_SUBMIT_OK:
                case ORDER_CLOSE_OK:
                case ORDERS_MERGE_OK:
                    //console.getInfo().println(orderLabel +" "+ messageType);
                    break;
                case NOTIFICATION:
                    //console.getNotif().println(orderLabel +" "+ message.getContent().replaceAll(".*-Order", "Order"));
                    break;
                case ORDER_CHANGED_REJECTED:
                case ORDER_CLOSE_REJECTED:
                case ORDER_FILL_REJECTED:
                case ORDER_SUBMIT_REJECTED:
                case ORDERS_MERGE_REJECTED:
                    //console.getWarn().println(orderLabel +" "+ messageType);
                    break;
                default:
                    //console.getErr().println(orderLabel +" *"+ messageType +"* "+ message.getContent());
                    break;
            }
        }
    }

    public void onStop() throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != this.instrument) {
            return;
        }

        int ordersTotal = engine.getOrders(this.instrument).size();

        if (ordersTotal > 0 && !this.volatileBarDetected) {
          IBar lastSlowBar = getBar(slowTrailsTimeFrame, stopLossOfferSide(), 0);
          double lastSlowBarPips = Math.abs(lastSlowBar.getLow() - lastSlowBar.getHigh()) / this.instrument.getPipValue();
          if (lastSlowBarPips > volatileBarPips && barColorRightForFastTrails(lastSlowBar)) {
            this.volatileBarDetected = true;
          }
        }

        if(filledOrdersCount() == 0 && this.volatileBarDetected) {
          this.console.getOut().println("Fast trailing finished. Closing all remaining orders.");
          closeAllOrders();
          // context.stop();
          // debug
          this.volatileBarDetected = false;
          this.entriesClosedByRiskManagement = 0;
          this.closeLimitLevel = -1;
          this.closeLimitLevelForStopLoss = -1;
          this.firstOrderTriggered = false;
        }
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        Period timeFrame = null;
        double trailsMultiplier = 0.0;
        int lookback = 0;
        if (this.volatileBarDetected) {
          timeFrame = fastTrailsTimeFrame;
          trailsMultiplier = fastTrailsMultiplier;
          lookback = fastTrailsLookback;
        } else {
          timeFrame = slowTrailsTimeFrame;
          trailsMultiplier = slowTrailsMultiplier;
          lookback = slowTrailsLookback;
        }




        if (instrument != this.instrument || period != timeFrame) {
            return;
        }



        int ordersTotal = engine.getOrders(instrument).size();
        // debug
        // if(ordersTotal == 0) {
        //     IBar lastBar = getBar(slowTrailsTimeFrame, OfferSide.ASK, 1);
        //     this.closeLimitLevel = lastBar.getHigh();
        //     engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELL, 0.02, askBar.getClose(), 3.0, getRoundedPrice(askBar.getClose() + (250 * instrument.getPipValue())), getRoundedPrice(askBar.getClose() - (50 * instrument.getPipValue()))).waitForUpdate(500);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUYSTOP, 0.02, askBar.getClose() + 45 * instrument.getPipValue(), 3.0, getRoundedPrice(askBar.getClose() - 100 * instrument.getPipValue()), 0.0);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUYSTOP, 0.02, askBar.getClose() + 75 * instrument.getPipValue(), 3.0, getRoundedPrice(askBar.getClose() - 100 * instrument.getPipValue()), 0.0);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUYSTOP, 0.02, askBar.getClose() + 105 * instrument.getPipValue(), 3.0, getRoundedPrice(askBar.getClose() - 100 * instrument.getPipValue()), 0.0);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUYSTOP, 0.02, askBar.getClose() + 135 * instrument.getPipValue(), 3.0, getRoundedPrice(askBar.getClose() - 100 * instrument.getPipValue()), 0.0);
        //     engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELLSTOP, 0.02, askBar.getClose() - 75 * instrument.getPipValue(), 3.0, getRoundedPrice(askBar.getClose() + 30 * instrument.getPipValue()), 0.0).waitForUpdate(500);
        //     engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELLSTOP, 0.02, askBar.getClose() - 105 * instrument.getPipValue(), 3.0, getRoundedPrice(askBar.getClose() + 30 * instrument.getPipValue()), 0.0).waitForUpdate(500);
        //     engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELLSTOP, 0.02, askBar.getClose() - 135 * instrument.getPipValue(), 3.0, getRoundedPrice(askBar.getClose() + 30 * instrument.getPipValue()), 0.0).waitForUpdate(500);
        //     setTrailingStopsToAllOrders();
        // }





        double upperBand = 0.0,upperBandPrev = 0.0;
        double bottomBand = 0.0,bottomBandPrev = 0.0;

        int dataSize = Math.max(500,4*lookback);
        double[] resATR = indicators.atr(instrument, timeFrame, stopLossOfferSide(), lookback, Filter.WEEKENDS, dataSize,askBar.getTime(), 0);
        List<IBar> prevBars = history.getBars(instrument, timeFrame, stopLossOfferSide(), Filter.WEEKENDS, dataSize, askBar.getTime(), 0);

        try
        {
         upperBandPrev = prevBars.get(0).getHigh() + trailsMultiplier*resATR[0];


         for (int i = 1; i < prevBars.size(); i++)
         {
             if(upperBand > 0.0) upperBandPrev = upperBand;
             upperBand = prevBars.get(i).getHigh() + trailsMultiplier*resATR[i];

             if(upperBandPrev <= prevBars.get(i - 1).getHigh()) continue;

             if(upperBandPrev <= upperBand)
             {
                 upperBand = upperBandPrev;
                 continue;
             }

         }


         bottomBandPrev = prevBars.get(0).getLow() - trailsMultiplier*resATR[0];

         for (int i = 1; i < prevBars.size(); i++)
         {
             if(bottomBand > 0.0) bottomBandPrev = bottomBand;
             bottomBand = prevBars.get(i).getLow() - trailsMultiplier*resATR[i];
             if(bottomBandPrev >= prevBars.get(i - 1).getLow()) continue;

             if(bottomBandPrev >= bottomBand)
             {
                 bottomBand = bottomBandPrev;
                 continue;
             }

         }

        }
        catch (Exception e)
        {
              this.console.getErr().println(e.getMessage());
        };


        upperBand = getRoundedPrice(upperBand);
        bottomBand = getRoundedPrice(bottomBand);


        if (!this.volatileBarDetected) {
          IBar lastBar = getBar(slowTrailsTimeFrame, stopLossOfferSide(), 1);

            if (isLong() && lastBar.getClose() < this.closeLimitLevel && this.closeLimitLevel != -1) {
              closeAllOrders();
              this.console.getOut().println("EXITING");
              context.stop();
              // debug
              // this.volatileBarDetected = false;
              // this.entriesClosedByRiskManagement = 0;
              // this.closeLimitLevel = -1;
              // this.closeLimitLevelForStopLoss = -1;
              // this.firstOrderTriggered = false;

              return;
            }
            if (!isLong() && lastBar.getClose() > this.closeLimitLevel && this.closeLimitLevel != -1) {
              closeAllOrders();
              this.console.getOut().println("EXITING");
              context.stop();
              // debug
              // this.volatileBarDetected = false;
              // this.entriesClosedByRiskManagement = 0;
              // this.closeLimitLevel = -1;
              // this.closeLimitLevelForStopLoss = -1;
              // this.firstOrderTriggered = false;

              return;
            }

          if (isLong() && this.prevBandCached > lastBar.getLow() && this.firstOrderTriggered || !isLong() && this.prevBandCached < lastBar.getHigh() && this.firstOrderTriggered) {
            if (isLong()) {
              if (this.closeLimitLevel == -1 || this.closeLimitLevel < lastBar.getLow()) {
                moveAllStopLossesToCachedLevel();
                cacheCloseLimitLevelForStopLoss();
                this.closeLimitLevel = lastBar.getLow();
              }
            } else {
              if (this.closeLimitLevel == -1 || this.closeLimitLevel > lastBar.getHigh()) {
                moveAllStopLossesToCachedLevel();
                cacheCloseLimitLevelForStopLoss();
                this.closeLimitLevel = lastBar.getHigh();
              }
            }
          }
        }

        if (isLong()) {
          this.prevBandCached = getRoundedPrice(bottomBand);
        } else {
          this.prevBandCached = getRoundedPrice(upperBand);
        }






        if (!this.volatileBarDetected || !this.firstOrderTriggered) {
          this.console.getOut().println("No volatile bar or no second order triggered -> not trailing");
          return;
        }

        if (isLong()) {
          setAllStopLossesTo(bottomBand);
        } else {
          setAllStopLossesTo(upperBand);
        }
    }

    protected String getLabel(Instrument instrument) {
        return instrument.name().substring(0, 2) + instrument.name().substring(3, 5) + "DDtrails" + this.context.getTime() + Integer.toString(++this.ordersCounter);
    }

    protected double getRoundedPrice(double price) {
        BigDecimal bd = new BigDecimal(price);
        bd = bd.setScale(this.instrument.getPipScale() + 1, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

   protected void closeAllOrders() throws JFException {
      for (IOrder order : engine.getOrders(this.instrument)) {
        order.close();
      }
  }

    protected int filledOrdersCount() throws JFException {
      int filledOrdersTotal = 0;
      for (IOrder order : engine.getOrders(this.instrument)) {
        if (order.getState() == IOrder.State.FILLED) {
          filledOrdersTotal += 1;
        }
      }
      return filledOrdersTotal;
    }

    protected int totalOrdersCount() throws JFException {
      int filledOrdersTotal = 0;
      for (IOrder order : engine.getOrders(this.instrument)) {
          filledOrdersTotal += 1;
      }
      return filledOrdersTotal;
    }


  void setAllStopLossesTo(double price) throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      if (isLong() && price > order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      if (!isLong() && price < order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      order.waitForUpdate(5000);
    }
  }

  // void handleTooMuchRisk() throws JFException {
  //   int nonBreakEvenOrdersCount = 0;
  //   for (IOrder order : engine.getOrders(this.instrument)) {
  //     if (order.getState() == IOrder.State.FILLED) {
  //       if (isLong()) {
  //         if (order.getOpenPrice() > order.getStopLossPrice()) {
  //           nonBreakEvenOrdersCount++;
  //         }
  //       } else {
  //         if (order.getOpenPrice() < order.getStopLossPrice()) {
  //           nonBreakEvenOrdersCount++;
  //         }
  //       }
  //     }
  //   }
  //   if (nonBreakEvenOrdersCount > 2 && entriesClosedByRiskManagement < maxEntriesClosedByRiskManagement) {
  //     entriesClosedByRiskManagement++;
  //     orderWithBiggestProfit().close();
  //   }
  // }

  // IOrder orderWithBiggestProfit() throws JFException {
  //   IOrder result = null;
  //   for (IOrder order : engine.getOrders(this.instrument)) {
  //     if (order.getState() == IOrder.State.FILLED) {
  //       if (result == null || result.getProfitLossInUSD() < order.getProfitLossInUSD()) {
  //         result = order;
  //       }
  //     }
  //   }
  //   return result;
  // }

  void setTrailingStopsToAllOrders() throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      if (order.getTrailingStep() == 0) {
        double currentPrice = order.getStopLossPrice();
        order.setStopLossPrice(currentPrice, stopLossOfferSide(), trailingStopsPips);
      }
    }
    this.console.getOut().println("All orders trailing stops set");
  }

  void moveAllStopLossesToCachedLevel() throws JFException {
    if (this.closeLimitLevelForStopLoss == -1 || !this.firstOrderTriggered) {
      return;
    }
    setAllStopLossesTo(this.closeLimitLevelForStopLoss);
    this.console.getOut().println("Moving all stop losses to high before last");
  }

  void cacheCloseLimitLevelForStopLoss() throws JFException {
    double pipsDiff = Math.abs(this.closeLimitLevelForStopLoss - this.closeLimitLevel) / this.instrument.getPipValue();
    if (this.closeLimitLevelForStopLoss != -1 && pipsDiff < minimumExtremesDistanceForStopLossJumpPips) {
      return;
    }
    if (this.closeLimitLevelForStopLoss == -1 || pipsDiff >= minimumExtremesDistanceForStopLossJumpPips) {
      this.closeLimitLevelForStopLoss = this.closeLimitLevel;
    }
  }

  boolean isLong() throws JFException {
    List<IOrder> orders = engine.getOrders(this.instrument);
    return orders.get(0).isLong();
  }

  boolean barColorRightForFastTrails(IBar bar) throws JFException {
    boolean isRed = bar.getClose() < bar.getOpen();
    boolean result = false;
    if (isLong()) {
      if (!isRed) {
        result = true;
      }
    }
    if (!isLong()) {
      if (isRed) {
        result = true;
      }
    }
    return result;
  }

  protected IBar getBar(Period timeFrame, OfferSide marketSide, int index) throws JFException {
    IBar lastBar = history.getBar(
      this.instrument,
      timeFrame,
      marketSide,
      index
    );
    return(lastBar);
  }

  OfferSide stopLossOfferSide() throws JFException {
    if (isLong()) {
      return OfferSide.BID;
    } else {
      return OfferSide.ASK;
    }
  }
}




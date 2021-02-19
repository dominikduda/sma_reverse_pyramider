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

public class TheSMAManager implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IContext context;
    private IHistory history;
    private IIndicators indicators;
    private IUserInterface userInterface;

    private int ordersCounter;
    private int entriesClosedByRiskManagement = 0;
    private double prevBandCached = -1;
    private boolean volatileBarDetected = false;

    private double closeLimitLevelForStopLoss = -1;
    private double closeLimitLevel = -1;
    private boolean firstOrderTriggered = false;


    private boolean firstExitPerformed = false;
    private boolean secondExitPerformed = false;
    private boolean thirdExitPerformed = false;

    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;

    @Configurable("Breakout level")
    public double breakoutLevel = -1;



    private IOrder order1 = null;
    private IOrder order2 = null;
    private IOrder order3 = null;

    private double initialStopPips = -1;

    private IFeedDescriptor feedDescriptor15;
    private IFeedDescriptor feedDescriptor5;

    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        this.ordersCounter = 0;
        // Do subscribe selected instrument
        Set subscribedInstruments = new HashSet();
        subscribedInstruments.add(this.instrument);
        context.setSubscribedInstruments(subscribedInstruments);
        int ordersTotal = engine.getOrders(this.instrument).size();
        if (ordersTotal == 2) {
          this.firstExitPerformed = true;
          this.order2 = engine.getOrders(this.instrument).get(1);
        }
        if (ordersTotal == 1) {
          this.order1 = engine.getOrders(this.instrument).get(0);
          this.firstExitPerformed = true;
          this.secondExitPerformed = true;
        }
        if (ordersTotal == 3) {
          this.order3 = engine.getOrders(this.instrument).get(2);
        }

        // comment_for_debug
        IOrder firstOrder = engine.getOrders(this.instrument).get(0);
        setAllStopLossesTo(firstOrder.getStopLossPrice());
        this.initialStopPips = Math.abs((firstOrder.getOpenPrice() - firstOrder.getStopLossPrice()) / instrument.getPipValue());
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        // Print messages, but related to own orders
        if (message.getOrder() != null) {
            if (message.getOrder().getInstrument() != this.instrument) {
              return;
            }
            IMessage.Type messageType = message.getType();
            switch (messageType) {
                case ORDER_FILL_OK:
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

        if(filledOrdersCount() == 0) {
          // comment for debug
          context.stop();
        }
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
      Period timeFrame = null;

      int ordersTotal = engine.getOrders(this.instrument).size();

      timeFrame = Period.FIFTEEN_MINS;
      // debug code
      // if (instrument == this.instrument && period == Period.FIFTEEN_MINS && ordersTotal == 0) {
            // IBar lastBar = getBar(timeFrame, OfferSide.ASK, 1);
            // this.closeLimitLevel = lastBar.getHigh();
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELL, 0.02, askBar.getClose(), 3.0, getRoundedPrice(askBar.getClose() + (5 * instrument.getPipValue())), 0).waitForUpdate(5000);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELL, 0.02, askBar.getClose(), 3.0, getRoundedPrice(askBar.getClose() + (5 * instrument.getPipValue())), 0).waitForUpdate(5000);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELL, 0.02, askBar.getClose(), 3.0, getRoundedPrice(askBar.getClose() + (5 * instrument.getPipValue())), 0).waitForUpdate(5000);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUY, 0.02, askBar.getClose(), 3.0, getRoundedPrice(askBar.getClose() - (5 * instrument.getPipValue())), 0).waitForUpdate(500);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUY, 0.02, askBar.getClose(), 3.0, getRoundedPrice(askBar.getClose() - (5 * instrument.getPipValue())), 0).waitForUpdate(500);
            // engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUY, 0.02, askBar.getClose(), 3.0, getRoundedPrice(askBar.getClose() - (5 * instrument.getPipValue())), 0).waitForUpdate(500);
            // this.firstExitPerformed = false;
            // this.secondExitPerformed = false;
            // this.thirdExitPerformed = false;
            // IOrder firstOrder = engine.getOrders(this.instrument).get(0);
            // setAllStopLossesTo(firstOrder.getStopLossPrice());
            // this.initialStopPips = Math.abs((firstOrder.getOpenPrice() - firstOrder.getStopLossPrice()) / instrument.getPipValue());
            // this.breakoutLevel = askBar.getClose() + (5 * instrument.getPipValue());
            // this.order1 = engine.getOrders(this.instrument).get(0);
            // this.order2 = engine.getOrders(this.instrument).get(1);
            // this.order3 = engine.getOrders(this.instrument).get(2);
      // }

      if (period == Period.FIVE_MINS && ordersTotal > 0) {
        if (breakoutLevel != -1 && (isLong() && bidBar.getClose() > breakoutLevel || !isLong() && askBar.getClose() < breakoutLevel)) {
          double smaForTrail = indicators.sma(instrument, Period.FIVE_MINS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);
          if ((isLong() && bidBar.getClose() > smaForTrail) || (!isLong() && askBar.getClose() < smaForTrail)) {
            IOrder firstOrder = engine.getOrders(this.instrument).get(0);
            double currentStopLossPips = Math.abs(firstOrder.getOpenPrice() - firstOrder.getStopLossPrice()) / instrument.getPipValue();
            if (currentStopLossPips <= this.initialStopPips) {
              setAllStopLossesTo(smaForTrail);
            }
          }
        }
      }


      if (period != Period.FIFTEEN_MINS) {
        return;
      }

      // TOP AND BOTTOM BAND generation
      long prevBarTime = history.getPreviousBarStart(Period.FIFTEEN_MINS, askBar.getTime());
      List<IBar> askBars = history.getBars(instrument, Period.FIFTEEN_MINS, OfferSide.ASK, Filter.WEEKENDS, 155, askBar.getTime(), 0);
      double topSlowBand = Double.NEGATIVE_INFINITY;
      for (IBar bar : askBars) {
        if (bar.getHigh() > topSlowBand) {
          topSlowBand = bar.getHigh();
        }
      }
      List<IBar> askBars2 = history.getBars(instrument, Period.FIFTEEN_MINS, OfferSide.ASK, Filter.WEEKENDS, 55, askBar.getTime(), 0);
      double topBaseBand = Double.NEGATIVE_INFINITY;
      for (IBar bar : askBars2) {
        if (bar.getHigh() > topBaseBand) {
          topBaseBand = bar.getHigh();
        }
      }
      List<IBar> askBars3 = history.getBars(instrument, Period.FIFTEEN_MINS, OfferSide.ASK, Filter.WEEKENDS, 14, askBar.getTime(), 0);
      double topFastBand = Double.NEGATIVE_INFINITY;
      for (IBar bar : askBars3) {
        if (bar.getHigh() > topFastBand) {
          topFastBand = bar.getHigh();
        }
      }

      List<IBar> bidBars = history.getBars(instrument, Period.FIFTEEN_MINS, OfferSide.BID, Filter.WEEKENDS, 155, bidBar.getTime(), 0);
      double botSlowBand = Double.POSITIVE_INFINITY;
      for (IBar bar : bidBars) {
        if (bar.getLow() < botSlowBand) {
          botSlowBand = bar.getLow();
        }
      }
      List<IBar> bidBars2 = history.getBars(instrument, Period.FIFTEEN_MINS, OfferSide.BID, Filter.WEEKENDS, 55, bidBar.getTime(), 0);
      double botBaseBand = Double.POSITIVE_INFINITY;
      for (IBar bar : bidBars2) {
        if (bar.getLow() < botBaseBand) {
          botBaseBand = bar.getLow();
        }
      }
      List<IBar> bidBars3 = history.getBars(instrument, Period.FIFTEEN_MINS, OfferSide.BID, Filter.WEEKENDS, 14, bidBar.getTime(), 0);
      double botFastBand = Double.POSITIVE_INFINITY;
      for (IBar bar : bidBars3) {
        if (bar.getLow() < botFastBand) {
          botFastBand = bar.getLow();
        }
      }

      if (isLong()) {
        if (this.order1.getState() == IOrder.State.FILLED && this.order1.getStopLossPrice() < botFastBand) {
          this.order1.setStopLossPrice(botFastBand);
          this.order1.waitForUpdate(500);
        }
        if (this.order2.getState() == IOrder.State.FILLED && this.order2.getStopLossPrice() < botBaseBand) {
          this.order2.setStopLossPrice(botBaseBand);
          this.order2.waitForUpdate(500);
        }
        if (this.order3.getState() == IOrder.State.FILLED && this.order3.getStopLossPrice() < botSlowBand) {
          this.order3.setStopLossPrice(botSlowBand);
          this.order3.waitForUpdate(500);
        }
      }
      if (!isLong()) {
        if (this.order1.getState() == IOrder.State.FILLED && this.order1.getStopLossPrice() > topFastBand) {
          this.order1.setStopLossPrice(topFastBand);
          this.order1.waitForUpdate(500);
        }
        if (this.order2.getState() == IOrder.State.FILLED && this.order2.getStopLossPrice() > topBaseBand) {
          this.order2.setStopLossPrice(topBaseBand);
          this.order2.waitForUpdate(500);
        }
        if (this.order3.getState() == IOrder.State.FILLED && this.order3.getStopLossPrice() > topSlowBand) {
          this.order3.setStopLossPrice(topSlowBand);
          this.order3.waitForUpdate(500);
        }
      }


      // timeFrame = Period.FIFTEEN_MINS;
      // if (instrument == this.instrument && period == Period.FIFTEEN_MINS && ordersTotal > 0) {



      //   double SMAflash = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 0);
      //   double SMAsecond = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);
      //   double SMAfastBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 45, 0);
      //   double SMAslowBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 100, 0);
      //   double prevSMAflash = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 1);
      //   double prevSMAsecond = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 1);
      //   double prevSMAfastBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 45, 1);
      //   double prevSMAslowBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 100, 1);


 //        if (!this.firstExitPerformed) {
 //          IOrder maxAmountOrder = engine.getOrders(this.instrument).get(0);
 //          for (IOrder order : engine.getOrders(this.instrument)) {
 //            if (order.getAmount() > maxAmountOrder.getAmount()) {
 //              maxAmountOrder = order;
 //            }
 //          }

 //          if (isLong()) {
 //            if (prevSMAflash > prevSMAsecond && SMAflash < SMAsecond && maxAmountOrder.getProfitLossInPips() > 0) {
 //              maxAmountOrder.close();
 //              maxAmountOrder.waitForUpdate(5000);
 //              ordersTotal = engine.getOrders(this.instrument).size();
 //              if (ordersTotal < 3) {
 //                this.firstExitPerformed = true;
 //              }
 //              return;
 //            }
 //          }

 //          if (!isLong()) {
 //            if (prevSMAflash < prevSMAsecond && SMAflash > SMAsecond && maxAmountOrder.getProfitLossInPips() > 0) {
 //              maxAmountOrder.close();
 //                    console.getInfo().println("1");
 //              maxAmountOrder.waitForUpdate(5000);
 //              ordersTotal = engine.getOrders(this.instrument).size();
 //              if (ordersTotal < 3) {
 //                this.firstExitPerformed = true;
 //              }
 //              return;
 //            }
 //          }

 //        }


 //      timeFrame = Period.FIFTEEN_MINS;
 //      if (this.firstExitPerformed && !this.secondExitPerformed) {
 //        double finalSMAfastBaseline = indicators.sma(this.instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);
 //        double finalSMAslowBaseline = indicators.sma(this.instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 45, 0);
 //        double finalprevSMAfastBaseline = indicators.sma(this.instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 1);
 //        double finalprevSMAslowBaseline = indicators.sma(this.instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 45, 1);
 //        IOrder firstOrder = engine.getOrders(this.instrument).get(0);
 //        if (isLong()) {
 //          if (finalprevSMAfastBaseline > finalprevSMAslowBaseline && finalSMAfastBaseline < finalSMAslowBaseline) {
 //            firstOrder.close();
 //            this.secondExitPerformed = true;
 //            return;
 //          }
 //        }
 //        if (!isLong()) {
 //          if (finalprevSMAfastBaseline < finalprevSMAslowBaseline && finalSMAfastBaseline > finalSMAslowBaseline) {
 //            firstOrder.close();
 //                    console.getInfo().println("2");
 //            this.secondExitPerformed = true;
 //            return;
 //          }
 //        }
 //        }

 //        timeFrame = Period.FIFTEEN_MINS;
 // double middleLine = indicators.sma(
 //                            this.feedDescriptor15, AppliedPrice.CLOSE, OfferSide.ASK, 200)
 //                            .calculate(3, askBar.getTime(), 0)[0];
 //        if (period == Period.FIFTEEN_MINS && this.secondExitPerformed && !this.thirdExitPerformed) {
 //          IOrder lastOrder = engine.getOrders(this.instrument).get(0);
 //          if (isLong()) {
 //            if (bidBar.getClose() < middleLine) {
 //              lastOrder.close();
 //              this.thirdExitPerformed = true;
 //              return;
 //            }
 //          }
 //          if (!isLong()) {
 //                      console.getInfo().println(middleLine);
 //            if (askBar.getClose() > middleLine) {
 //                      console.getInfo().println("3");
 //              lastOrder.close();
 //              this.thirdExitPerformed = true;
 //          return;
 //            }
 //          }
 //        }

 //      }

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
    protected String getLabel(Instrument instrument) {
        return instrument.name().substring(0, 2) + instrument.name().substring(3, 5) + "DDtrails" + this.context.getTime() + Integer.toString(++this.ordersCounter);
    }
}





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

public class TheSMAManagerB implements IStrategy {
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

    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;


    private IOrder order = null;


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
        }
        if (ordersTotal == 1) {
          this.firstExitPerformed = true;
          this.secondExitPerformed = true;
        }
        // if (ordersTotal != 4) {
        //   console.getNotif().println("4 orders must be present!");
        //   context.stop();
        // }
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
          closeAllOrders();
          context.stop();
        }
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
      Period timeFrame = null;




      timeFrame = Period.FIFTEEN_MINS;
      if (instrument == this.instrument && period == Period.FIFTEEN_MINS) {
        double SMAflash = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 1);
        double SMAsecond = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 1);
        double SMAfastBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 45, 1);
        double SMAslowBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 100, 1);
        double prevSMAflash = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 2);
        double prevSMAsecond = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 2);
        double prevSMAfastBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 45, 2);
        double prevSMAslowBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 100, 2);


        if (!this.firstExitPerformed) {
          IOrder maxAmountOrder = engine.getOrders(this.instrument).get(0);
          for (IOrder order : engine.getOrders(this.instrument)) {
            if (order.getAmount() > maxAmountOrder.getAmount()) {
              maxAmountOrder = order;
            }
          }

          if (isLong()) {
            if (prevSMAflash > prevSMAsecond && SMAflash < SMAsecond) {
              maxAmountOrder.close();
              this.firstExitPerformed = true;
            }
          }

          if (!isLong()) {
            if (prevSMAflash < prevSMAsecond && SMAflash > SMAsecond) {
              maxAmountOrder.close();
              maxAmountOrder.waitForUpdate(5000);
              int ordersTotal = engine.getOrders(this.instrument).size();
              if (ordersTotal < 3) {
                this.firstExitPerformed = true;
                IOrder sampleOrder = engine.getOrders(this.instrument).get(0);
                setAllStopLossesTo(sampleOrder.getOpenPrice());
              }
            }
          }

          if (this.firstExitPerformed && !this.secondExitPerformed) {
            IOrder firstOrder = engine.getOrders(this.instrument).get(0);
            if (isLong()) {
              if (prevSMAfastBaseline > prevSMAslowBaseline && SMAfastBaseline < SMAslowBaseline) {
                firstOrder.close();
                this.secondExitPerformed = true;
              }
            }
            if (!isLong()) {
              if (prevSMAfastBaseline < prevSMAslowBaseline && SMAfastBaseline > SMAslowBaseline) {
                firstOrder.close();
                this.secondExitPerformed = true;
              }
            }
          }
        }

      timeFrame = Period.FIFTEEN_MINS;
      if (instrument == this.instrument && period == Period.FIFTEEN_MINS && this.secondExitPerformed) {
        double finalSMAfastBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 1);
        double finalSMAslowBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 200, 1);
        double finalprevSMAfastBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 2);
        double finalprevSMAslowBaseline = indicators.sma(instrument, timeFrame, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 200, 2);
        IOrder firstOrder = engine.getOrders(this.instrument).get(0);
        if (isLong()) {
          if (finalprevSMAfastBaseline > finalprevSMAslowBaseline && finalSMAfastBaseline < finalSMAslowBaseline) {
            firstOrder.close();
          }
        }
        if (!isLong()) {
          if (finalprevSMAfastBaseline < finalprevSMAslowBaseline && finalSMAfastBaseline > finalSMAslowBaseline) {
            firstOrder.close();
          }
        }
      }
    }
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
}




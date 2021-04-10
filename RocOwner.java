package jforex;

import com.dukascopy.api.*;
import java.math.*;
import com.dukascopy.api.IEngine.OrderCommand;
import java.text.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.util.TimePeriodAggregationFeedDescriptor;

public class RocOwner implements IStrategy {
  private IEngine engine;
  private IConsole console;
  private IContext context;
  private IHistory history;
  private IIndicators indicators;
  private IUserInterface userInterface;

  @Configurable("Instrument")
  public Instrument instrument = null;

  private int closedM15CandlesTillBotStart = 0;
  private boolean shouldCloseAllOrders = false;

  private double extremeForTrail = -1;

  private boolean entrySignalForLong = false;
  private boolean entrySignalForShort = false;
  private boolean prevIsUptrend = false;
  private int ordersCounter = 0;
  private int longSignalValidity = 8;
  private int shortSignalValidity = 8;

  public void onStart(IContext context) throws JFException {
    if (instrument == null) {
      throw new JFException("You must provide instrumetnt");
    }
    this.console = context.getConsole();
    this.engine = context.getEngine();
    this.history = context.getHistory();
    this.context = context;
    this.indicators = context.getIndicators();
    this.userInterface = context.getUserInterface();
    // Do subscribe selected instrument
    Set subscribedInstruments = new HashSet();
    subscribedInstruments.add(this.instrument);
    context.setSubscribedInstruments(subscribedInstruments);
  }

  public void onAccount(IAccount account) throws JFException {}

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
          break;
        case NOTIFICATION:
          break;
        case ORDER_CHANGED_REJECTED:
        case ORDER_CLOSE_REJECTED:
        case ORDER_FILL_REJECTED:
        case ORDER_SUBMIT_REJECTED:
        case ORDERS_MERGE_REJECTED:
          break;
        default:
          break;
      }
    }
  }

  public void onStop() throws JFException {}

  public void onTick(Instrument instrument, ITick tick) throws JFException {
    if (instrument != this.instrument) {
      return;
    }

    // if (filledOrdersCount() == 0) {
    //   console.getOut().println("No orders present. Script stopped.");
    //   context.stop();
    // }

  }

  public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    if (instrument != this.instrument) {
      return;
    }

    if (period != Period.FIFTEEN_MINS) {
      return;
    }

    double prevEMAflash =
        indicators.ema(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 5, 0);
    double prevPrevEMAflash =
        indicators.ema(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 5, 1);
    double prevEMAslow =
        indicators.ema(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 33, 0);
    double prevPrevEMAslow =
        indicators.ema(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 33, 1);
    double prevEMAbaseline =
        indicators.ema(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, 0);
    double prevPrevEMAbaseline =
        indicators.ema(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, 1);

    this.longSignalValidity--;
    this.shortSignalValidity--;

    boolean isUptrend = prevEMAbaseline > prevPrevEMAbaseline;
    if ((this.prevIsUptrend == true && isUptrend == false) || this.longSignalValidity == 0) {
      this.entrySignalForLong = false;
      this.longSignalValidity = 20;
    }
    if ((this.prevIsUptrend == false && isUptrend == true) || this.shortSignalValidity == 0) {
      this.entrySignalForShort = false;
      this.shortSignalValidity = 20;
    }
    this.prevIsUptrend = isUptrend;



    int stopLossPips = 20;
    if (totalOrdersCount() == 0) {
      if (isUptrend && entrySignalForLong && prevPrevEMAflash < prevPrevEMAslow && prevEMAflash > prevEMAslow) {
          double stopLossForLong = bidBar.getClose() - (stopLossPips * instrument.getPipValue());
          engine
              .submitOrder(
                  getLabel(instrument),
                  instrument,
                  OrderCommand.BUY,
                  0.02,
                  askBar.getClose(),
                  3.0,
                  getRoundedPrice(stopLossForLong),
                  0);
        this.entrySignalForLong = false;
        this.longSignalValidity = 20;
      }
      if (!isUptrend && entrySignalForShort && prevPrevEMAflash > prevPrevEMAslow && prevEMAflash < prevEMAslow) {
          double stopLossForShort = askBar.getClose() + (stopLossPips * instrument.getPipValue());
            engine
              .submitOrder(
                  getLabel(instrument),
                  instrument,
                  OrderCommand.SELL,
                  0.02,
                  bidBar.getClose(),
                  3.0,
                  getRoundedPrice(stopLossForShort),
                  0);
        this.entrySignalForShort = false;
        this.shortSignalValidity = 20;
      }
    }

    if (totalOrdersCount() > 0) {
      if (isLong() && prevPrevEMAflash > prevPrevEMAslow && prevEMAflash < prevEMAslow) {
        closeAllOrders();
      }
      if (!isLong() && prevPrevEMAflash < prevPrevEMAslow && prevEMAflash > prevEMAslow) {
        closeAllOrders();
      }
    }

    // extract to arg later potentially
    int getRocForCandlesBack = 800;
    double percentOfExtrermeDataPoints = 0.15;

    IBar startingBar = history.getBar(instrument, Period.FIFTEEN_MINS, OfferSide.ASK, getRocForCandlesBack);
    IBar endingBar = history.getBar(instrument, Period.FIFTEEN_MINS, OfferSide.ASK, 1);

    double[] rocM15 = indicators.roc(
      instrument,
      Period.FIFTEEN_MINS,
      OfferSide.BID,
      IIndicators.AppliedPrice.CLOSE,
      8,
      startingBar.getTime(),
      endingBar.getTime()
    );

    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < getRocForCandlesBack; i++) {
      if (rocM15[i] < min) {
        min = rocM15[i];
      }
      if (rocM15[i] > max) {
        max = rocM15[i];
      }
    }
    double heightOfRocChart = Math.abs(min) + Math.abs(max);
    // configurable
    int heightSplitCount = 60;
    double singleIncrement = heightOfRocChart / heightSplitCount;

    double extremeHigh = Double.NEGATIVE_INFINITY;
    double extremeLow = Double.POSITIVE_INFINITY;
    for (int j = 1; j <= heightSplitCount; j++) {
      double measuredLevel = min + singleIncrement * j;
      int pointsAboveLevel = 0;
      int pointsBelowLevel = 0;
      for (int i = 0; i < getRocForCandlesBack; i++) {
        if (rocM15[i] < measuredLevel) {
          pointsBelowLevel++;
        }
        if (rocM15[i] > measuredLevel) {
          pointsAboveLevel++;
        }
      }
      if (((double) pointsBelowLevel) / getRocForCandlesBack < percentOfExtrermeDataPoints) {
        extremeLow = measuredLevel;
      }

      if (((double) pointsAboveLevel) / getRocForCandlesBack < percentOfExtrermeDataPoints && extremeHigh == Double.NEGATIVE_INFINITY) {
        extremeHigh = measuredLevel;
      }
    }
    // console.getOut().println(extremeHigh);
    // console.getOut().println(extremeLow);

    if (rocM15[getRocForCandlesBack - 1] < extremeLow) {
      this.entrySignalForLong = true;
    }

    if (rocM15[getRocForCandlesBack - 1] > extremeHigh) {
      this.entrySignalForShort = true;
    }









    // handle close in loss because limit level exceeded
    // if (limitLevelForClose != -1) { // disabled - for mini stop
    //   IBar relevantBarForClose = null;
    //   if (isLong()) {
    //     relevantBarForClose = bidBar;
    //     if (relevantBarForClose.getClose() <= limitLevelForClose) {
    //       console.getOut().println("Close not above the safety. Exiting position.");
    //       closeAllOrders();
    //     }
    //   }
    //   if (!isLong()) {
    //     relevantBarForClose = askBar;
    //     if (relevantBarForClose.getClose() >= limitLevelForClose) {
    //       console.getOut().println("Close not below the safety level. Exiting position.");
    //       closeAllOrders();
    //     }
    //   }
    // }

    // // handle time out by 7 candles close
    // this.closedM15CandlesTillBotStart++;
    // if (limitLevelFor7Bars != -1) {
    //   IBar relevantBarForTimeout = null;
    //   if (isLong() && closedM15CandlesTillBotStart == 7) {
    //     relevantBarForTimeout = bidBar;
    //     if (relevantBarForTimeout.getClose() < limitLevelFor7Bars) {
    //       console.getOut().println("Too little profit in 7 candles. Exiting position.");
    //       closeAllOrders();
    //     }
    //   }
    //   if (!isLong() && closedM15CandlesTillBotStart == 7) {
    //     relevantBarForTimeout = askBar;
    //     if (relevantBarForTimeout.getClose() > limitLevelFor7Bars) {
    //       console.getOut().println("Too little profit in 7 candles. Exiting position.");
    //       closeAllOrders();
    //     }
    //   }
    // }

    // // handle opposite bar in profit
    // IOrder sampleOrder = engine.getOrders(this.instrument).get(0);
    // IBar relevantBar = null;
    // boolean skipCheckForThisCandle = ignoreInitialCandle && this.closedM15CandlesTillBotStart == 1;
    // if (isLong() && !skipCheckForThisCandle) {
    //   relevantBar = bidBar;
    //   boolean isRed = relevantBar.getClose() <= relevantBar.getOpen();
    //   if (sampleOrder.getProfitLossInPips() > minimalProfitPips && isRed) {
    //     console.getOut().println("Red bar closing on profit detected. Exiting position");
    //     closeAllOrders();
    //   }
    // }
    // if (!isLong() && !skipCheckForThisCandle) {
    //   relevantBar = askBar;
    //   boolean isGreen = relevantBar.getClose() >= relevantBar.getOpen();
    //   if (sampleOrder.getProfitLossInPips() > minimalProfitPips && isGreen) {
    //     console.getOut().println("Green bar closing on profit detected. Exiting position");
    //     closeAllOrders();
    //   }
    // }
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
    this.shouldCloseAllOrders = true;
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
      if (isLong() && getRoundedPrice(price) > order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      if (!isLong() && getRoundedPrice(price) < order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      order.waitForUpdate(2000);
      order.waitForUpdate(2000);
    }
  }

  void setAllTakeProfitsTo(double price) throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      order.setTakeProfitPrice(price);
      order.waitForUpdate(2000);
      order.waitForUpdate(2000);
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
  //   if (nonBreakEvenOrdersCount > 2 && entriesClosedByRiskManagement <
  // maxEntriesClosedByRiskManagement) {
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
    IBar lastBar = history.getBar(this.instrument, timeFrame, marketSide, index);
    return (lastBar);
  }

  OfferSide stopLossOfferSide() throws JFException {
    if (isLong()) {
      return OfferSide.BID;
    } else {
      return OfferSide.ASK;
    }
  }


  protected IBar getBar(Period timeFrame, OfferSide marketSide, int offset, long currentTime) throws JFException {
    long prevBarTime = history.getPreviousBarStart(timeFrame, currentTime);
    List<IBar> bars = history.getBars(
      instrument,
      timeFrame,
      marketSide,
      history.getTimeForNBarsBack(timeFrame, prevBarTime, offset + 1),
      prevBarTime
    );
    IBar resultBar = bars.get(1);
    return(resultBar);
  }

  protected boolean canEnterLong() throws JFException {
    double SMAflashH4 =
        indicators.sma(
            instrument, Period.FOUR_HOURS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 0);
    double SMAsecondH4 =
        indicators.sma(
            instrument, Period.FOUR_HOURS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);
    if (SMAflashH4 >= SMAsecondH4) {
      return true;
    } else {
      return false;
    }
  }

  protected String getLabel(Instrument instrument) {
    return instrument.name().substring(0, 2)
        + instrument.name().substring(3, 5)
        + "DDtrails"
        + this.context.getTime()
        + Integer.toString(++this.ordersCounter);
  }
}

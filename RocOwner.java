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
  private int longSignalValidity = 50;
  private int shortSignalValidity = 50;

  private double prevSMAflash1 = -1;
  private double prevSMAflash2 = -1;
  private double prevSMAflash3 = -1;
  private double prevSMAflash4 = -1;
  private double prevSMAflash5 = -1;

  private int fallsCount = 0;
  private int growsCount = 0;

  private int candlesInTrade = 0;

  private boolean prevIsGrowing = false;
  private boolean prevIsFalling = false;

  private boolean m15PartDone = false;
  private boolean h1PartDone = false;
  private boolean h4PartDone = false;

  private boolean beTaken = false;
  private boolean tpTaken = false;


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
          this.m15PartDone = false;
          this.h1PartDone = false;
          this.h4PartDone = false;
          this.beTaken = false;
          this.tpTaken = false;
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

    if (filledOrdersCount() > 0) {
      if (period == Period.FIFTEEN_MINS && !this.m15PartDone) {
        this.candlesInTrade++;
      }
      if (period == Period.ONE_HOUR && !this.h1PartDone) {
        this.candlesInTrade++;
      }
      if (period == Period.FOUR_HOURS && !this.h4PartDone) {
        this.candlesInTrade++;
      }
    }

    if (period != Period.FIFTEEN_MINS) {
      return;
    }


    if (filledOrdersCount() == 0 && this.candlesInTrade > 0) {
      this.candlesInTrade = 0;
    }

    if (this.candlesInTrade > 40 && !this.m15PartDone) {
      this.m15PartDone = true;
      this.candlesInTrade = this.candlesInTrade / 4;
      console.getOut().println("m15 part done");
    }

    if (this.candlesInTrade > 35 && !this.h1PartDone) {
      this.h1PartDone = true;
      this.candlesInTrade = this.candlesInTrade / 4;
      console.getOut().println("h1 part done");
    }

    if (this.candlesInTrade > 30 && !this.h4PartDone) {
      this.h4PartDone = true;
      console.getOut().println("h4 part done");
    }

    Period smasPeriod = null;
    if (!m15PartDone) {
      smasPeriod = Period.FIFTEEN_MINS;
    }
    if (m15PartDone) {
      smasPeriod = Period.ONE_HOUR;
    }
    if (h1PartDone) {
      smasPeriod = Period.FOUR_HOURS;
    }
    if (h4PartDone) {
      smasPeriod = Period.DAILY;
    }

    double prevEMAflash =
        indicators.ema(
            instrument, smasPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 5, 1);
    double prevPrevEMAflash =
        indicators.ema(
            instrument, smasPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 5, 2);
    double prevEMAslow =
        indicators.ema(
            instrument, smasPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 33, 1);
    double prevPrevEMAslow =
        indicators.ema(
            instrument, smasPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 33, 2);
    double prevEMAbaseline =
        indicators.ema(
            instrument, smasPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, 1);
    double prevPrevEMAbaseline =
        indicators.ema(
            instrument, smasPeriod, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, 2);

    this.prevSMAflash5 = this.prevSMAflash4;
    this.prevSMAflash4 = this.prevSMAflash3;
    this.prevSMAflash3 = this.prevSMAflash2;
    this.prevSMAflash2 = this.prevSMAflash1;
    this.prevSMAflash1 = prevEMAflash;

    this.longSignalValidity--;
    this.shortSignalValidity--;

    boolean isUptrend = prevEMAbaseline > prevPrevEMAbaseline;
    if ((this.prevIsUptrend == true && isUptrend == false) || this.longSignalValidity == 0) {
      this.entrySignalForLong = false;
      this.longSignalValidity = 50;
      this.fallsCount = 0;
    }
    if ((this.prevIsUptrend == false && isUptrend == true) || this.shortSignalValidity == 0) {
      this.entrySignalForShort = false;
      this.shortSignalValidity = 50;
      this.growsCount = 0;
    }
    this.prevIsUptrend = isUptrend;

    if (this.entrySignalForLong) {
      if (priceIsGrowing() && !this.prevIsGrowing) {
        this.growsCount++;
      }
      this.prevIsGrowing = priceIsGrowing();
    }

    if (this.entrySignalForShort) {
      if (priceIsFalling() && !this.prevIsFalling) {
        this.fallsCount++;
      }
      this.prevIsFalling = priceIsFalling();
    }

    if (totalOrdersCount() != 0 && !this.beTaken) {
      IOrder tmp_ord = engine.getOrders(this.instrument).get(0);
      double newPrice = tmp_ord.getOpenPrice();
      if (tmp_ord.getProfitLossInPips() > 35) {
        tmp_ord.setStopLossPrice(tmp_ord.getOpenPrice());
        this.beTaken = true;
      }
    }

    // this is useless and IMO should be removed.
    if (totalOrdersCount() != 0 && !this.tpTaken) {
      console.getOut().println("!!!!!!!!!!!!!!");
      IOrder sample_ord = engine.getOrders(this.instrument).get(0);
      if (isLong()) {
        if (sample_ord.getProfitLossInPips() > 120) {
          double slPrice = sample_ord.getOpenPrice() + (40 * instrument.getPipValue());
          sample_ord.setStopLossPrice(getRoundedPrice(slPrice));
          this.tpTaken = true;
        }
      }
      if (!isLong()) {
        if (sample_ord.getProfitLossInPips() > 120) {
          double slPrice = sample_ord.getOpenPrice() - (40 * instrument.getPipValue());
          sample_ord.setStopLossPrice(getRoundedPrice(slPrice));
          this.tpTaken = true;
        }
      }
    }




    int stopLossPips = 20;
    if (totalOrdersCount() == 0) {
      // if (growsCount == 3 && isUptrend && entrySignalForLong && prevPrevEMAflash < prevPrevEMAslow && prevEMAflash > prevEMAslow) {
      if (growsCount == 1 && isUptrend && entrySignalForLong) {
          double stopLossForLong = bidBar.getClose() - (stopLossPips * instrument.getPipValue());
          double tpForLong = 0;
          engine
              .submitOrder(
                  getLabel(instrument),
                  instrument,
                  OrderCommand.BUY,
                  1.182213,
                  askBar.getClose(),
                  3.0,
                  getRoundedPrice(stopLossForLong),
                  tpForLong);
        this.entrySignalForLong = false;
        this.longSignalValidity = 50;
        this.growsCount = 0;
        this.candlesInTrade = 0;
      }
      // if (fallsCount == 3 && !isUptrend && entrySignalForShort && prevPrevEMAflash > prevPrevEMAslow && prevEMAflash < prevEMAslow) {
      if (fallsCount == 3 && !isUptrend && entrySignalForShort) {
          double stopLossForShort = askBar.getClose() + (stopLossPips * instrument.getPipValue());
          double tpForShort = 0;
            engine
              .submitOrder(
                  getLabel(instrument),
                  instrument,
                  OrderCommand.SELL,
                  1.182213,
                  bidBar.getClose(),
                  3.0,
                  getRoundedPrice(stopLossForShort),
                  tpForShort);
        this.entrySignalForShort = false;
        this.shortSignalValidity = 50;
        this.fallsCount = 0;
        this.candlesInTrade = 0;
      }
    }

    if (totalOrdersCount() > 0) {
      IOrder sample_ord = engine.getOrders(this.instrument).get(0);
      if (isLong() && prevPrevEMAflash > prevPrevEMAslow && prevEMAflash < prevEMAslow && sample_ord.getProfitLossInPips() > stopLossPips) {
        closeAllOrders();
        this.candlesInTrade = 0;
    // console.getOut().println("closing long");
    // console.getOut().println(prevPrevEMAflash);
    // console.getOut().println(prevPrevEMAslow);
    // console.getOut().println(prevEMAflash);
    // console.getOut().println(prevEMAslow);
      }
      if (!isLong() && prevPrevEMAflash < prevPrevEMAslow && prevEMAflash > prevEMAslow && sample_ord.getProfitLossInPips() > stopLossPips) {
        closeAllOrders();
        this.candlesInTrade = 0;
    // console.getOut().println("closing short");
    // console.getOut().println(prevPrevEMAflash < prevPrevEMAslow);
    // console.getOut().println(prevEMAflash > prevEMAslow);
      }
    }

    // extract to arg later potentially
    int getRocForCandlesBack = 800;
    double percentOfExtrermeDataPoints = 0.2;

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
    int heightSplitCount = 200;
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

    if (rocM15[getRocForCandlesBack - 1] < extremeLow && filledOrdersCount() == 0) {
      this.entrySignalForLong = true;
    }

    if (rocM15[getRocForCandlesBack - 1] > extremeHigh && filledOrdersCount() == 0) {
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

  protected boolean priceIsFalling() {
    return (this.prevSMAflash1 < this.prevSMAflash2 && this.prevSMAflash2 < this.prevSMAflash3 && this.prevSMAflash3 < this.prevSMAflash4 && this.prevSMAflash4 < this.prevSMAflash5);
    // this.prevSMAflash5 = this.prevSMAflash4
    // this.prevSMAflash4 = this.prevSMAflash3
    // this.prevSMAflash3 = this.prevSMAflash2
    // this.prevSMAflash2 = this.prevSMAflash1
    // this.prevSMAflash1 = prevEMAflash;
  }

  protected boolean priceIsGrowing() {
    return (this.prevSMAflash1 > this.prevSMAflash2 && this.prevSMAflash2 > this.prevSMAflash3 && this.prevSMAflash3 > this.prevSMAflash4 && this.prevSMAflash4 > this.prevSMAflash5);
    // this.prevSMAflash5 = this.prevSMAflash4
    // this.prevSMAflash4 = this.prevSMAflash3
    // this.prevSMAflash3 = this.prevSMAflash2
    // this.prevSMAflash2 = this.prevSMAflash1
    // this.prevSMAflash1 = prevEMAflash;
  }

  protected String getLabel(Instrument instrument) {
    return instrument.name().substring(0, 2)
        + instrument.name().substring(3, 5)
        + "DDtrails"
        + this.context.getTime()
        + Integer.toString(++this.ordersCounter);
  }
}

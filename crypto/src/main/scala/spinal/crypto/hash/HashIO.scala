/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   Crypto                           **
**       \__ \/ /_/ // //  |/ / /| | / /    (c) Dolu, All rights reserved    **
**      ___/ / ____// // /|  / ___ |/ /___                                   **
**     /____/_/   /___/_/ |_/_/  |_/_____/  MIT Licence                      **
**                                                                           **
** Permission is hereby granted, free of charge, to any person obtaining a   **
** copy of this software and associated documentation files (the "Software"),**
** to deal in the Software without restriction, including without limitation **
** the rights to use, copy, modify, merge, publish, distribute, sublicense,  **
** and/or sell copies of the Software, and to permit persons to whom the     **
** Software is furnished to do so, subject to the following conditions:      **
**                                                                           **
** The above copyright notice and this permission notice shall be included   **
** in all copies or substantial portions of the Software.                    **
**                                                                           **
** THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS   **
** OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF                **
** MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.    **
** IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY      **
** CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT **
** OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR  **
** THE USE OR OTHER DEALINGS IN THE SOFTWARE.                                **
\*                                                                           */
package spinal.crypto.hash

import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.BusSlaveFactory


/**
  * Hash Core configuration
  */
case class HashCoreGeneric(dataWidth     : BitCount,
                           hashWidth     : BitCount,
                           hashBlockWidth: BitCount)


/**
  * Hash Core command
  */
case class HashCoreCmd(g: HashCoreGeneric) extends Bundle{
  val msg  = Bits(g.dataWidth)
  val size = UInt(log2Up(g.dataWidth.value / 8) bits)
}


/**
  * Hash Core response
  */
case class HashCoreRsp(g: HashCoreGeneric) extends Bundle{
  val digest = Bits(g.hashWidth)
}


/**
  * Hash Core IO
  */
case class HashCoreIO(g: HashCoreGeneric) extends Bundle with IMasterSlave{

  val init = in Bool
  val cmd  = Stream(Fragment(HashCoreCmd(g)))
  val rsp  = Flow(HashCoreRsp(g))

  override def asMaster() = {
    out(init)
    master(cmd)
    slave(rsp)
  }

  /** Drive IO from a bus */
  def driveFrom(busCtrl: BusSlaveFactory, baseAddress: Int = 0) = new Area {

    var addr = baseAddress

    /* Write operation */

    busCtrl.driveMultiWord(cmd.msg,   addr)
    addr += (widthOf(cmd.msg)/32)*4

    busCtrl.drive(cmd.size, addr)
    addr += 4

    busCtrl.drive(cmd.last, addr)
    addr += 4

    val initReg = busCtrl.drive(init, addr) init(False)
    initReg.clearWhen(initReg)
    addr += 4

    val validReg = busCtrl.drive(cmd.valid, addr) init(False)
    validReg.clearWhen(cmd.ready)
    addr += 4

    /* Read operation */

    val digest   = Reg(cloneOf(rsp.digest))
    val rspValid = Reg(Bool) init(False) setWhen(rsp.valid)

    when(rsp.valid){
      digest := rsp.digest
    }

    busCtrl.onRead(addr){
      when(rspValid){
        rspValid := False
      }
    }

    busCtrl.read(rspValid, addr)
    addr += 4

    busCtrl.readMultiWord(digest, addr)
    addr += (widthOf(digest)/32)*4


    //manage interrupts
    val interruptCtrl = new Area {
      val doneIntEnable = busCtrl.createReadAndWrite(Bool, address = addr, 0) init(False)
      val doneInt       = doneIntEnable & !rsp.valid
      val interrupt     = doneInt
    }
  }
}
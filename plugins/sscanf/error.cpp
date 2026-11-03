/*
 *  sscanf 2.15.1
 *
 *  Version: MPL 1.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is the sscanf 2.0 SA:MP plugin.
 *
 *  The Initial Developer of the Original Code is Alex "Y_Less" Cole.
 *  Portions created by the Initial Developer are Copyright (c) 2022
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *
 *      Cheaterman
 *      DEntisT
 *      Emmet_
 *      karimcambridge
 *      kalacsparty
 *      Kirima
 *      leHeix
 *      maddinat0r
 *      Southclaws
 *      Y_Less
 *      ziggi
 *
 *  Special Thanks to:
 *
 *      SA:MP Team past, present, and future.
 *      maddinat0r, for hosting the repo for a very long time.
 *      Emmet_, for his efforts in maintaining it for almost a year.
 */

#include "error.h"

int
	g_iErrorCode = 0,
	g_iErrorSpecifier = 0,
	g_iCurrentSpecifier = 0;

int
	GetErrorCode()
{
	return g_iErrorCode;
}

void
	SetErrorCode(int error, int specifier)
{
	if (error == 0)
	{
		g_iErrorCode = 0;
		g_iErrorSpecifier = 0;
		g_iCurrentSpecifier = specifier;
	}
	else
	{
		g_iErrorCode = error;
		g_iErrorSpecifier = g_iCurrentSpecifier;
	}
}

int
	GetErrorSpecifier()
{
	return g_iErrorSpecifier;
}

void
	IncErrorSpecifier()
{
	++g_iCurrentSpecifier;
}

int
	GetErrorCategory(int error)
{
	switch (error)
	{
	case 0:
		return 0;
	case 1:
	case 32:
	case 33:
	case 34:
	case 35:
	case 36:
	case 37:
	case 38:
	case 39:
	case 40:
	case 41:
	case 42:
	case 43:
	case 49:
	case 68:
	case 69:
	case 1002:
	case 1005:
	case 1006:
	case 1007:
	case 1008:
		return 65536;
	case 3:
	case 4:
	case 5:
	case 6:
	case 7:
	case 8:
	case 9:
	case 10:
	case 11:
	case 12:
	case 13:
	case 14:
	case 15:
	case 16:
	case 17:
	case 18:
	case 19:
	case 20:
	case 21:
	case 22:
	case 23:
	case 24:
	case 25:
	case 26:
	case 27:
	case 28:
	case 29:
	case 30:
	case 31:
	case 44:
	case 45:
	case 46:
	case 47:
	case 48:
	case 50:
	case 51:
	case 52:
	case 53:
	case 54:
	case 55:
	case 56:
	case 57:
	case 58:
	case 59:
	case 60:
	case 61:
	case 62:
	case 63:
	case 64:
	case 65:
	case 66:
	case 67:
	case 70:
	case 71:
	case 72:
	case 73:
		return 131072;
	case 1011:
	case 1012:
	case 1013:
	case 1014:
	case 1015:
	case 1016:
	case 1017:
	case 1018:
	case 1019:
		return 196608;
	case 1004:
		return 262144;
	case 1009:
		return 327680;
	case 1001:
		return 393216;
	case 2:
		return 458752;
	case 1003:
		return 524288;
	case 1010:
		return 589824;
	default:
		return -1;
	}
}


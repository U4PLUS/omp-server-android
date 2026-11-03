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

#pragma once

enum E_SSCANF_OPTIONS
{
	SSCANF_OPTIONS_NONE = 0,
	OLD_DEFAULT_NAME = 1,
	MATCH_NAME_PARTIAL = 2,
	CELLMIN_ON_MATCHES = 4,
	SSCANF_QUIET = 8,
	OLD_DEFAULT_KUSTOM = 16,
	MATCH_NAME_FIRST = 32,
	ERROR_CODE_IN_RET = 64,
	WARNINGS_AS_ERRORS = 128,
	ERROR_CATEGORY_ONLY = 256
};

void
	RestoreOpts(E_SSCANF_OPTIONS, int, int);

char
	GetSingleType(char ** format);

char *
	GetMultiType(char ** format);

char *
	GetMultiType(char ** format);

unsigned int
	GetUserString(char * string, char ** end);

int
	GetDec(char ** const input);

int
	GetOct(char ** const input);

int
	GetHex(char ** const input);

unsigned int
	GetColour(char ** const input, int * type, unsigned int alpha);

int
	GetBool(char ** const input);

int
	GetNumber(char ** const input);

bool
	GetLogical(char ** const input);

bool
	SkipDefault(char ** const str);

void
	SkipLength(char ** const input);

int
	GetLength(char ** const input, struct args_s & args);

bool
	FindDefaultStart(char ** const str);


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

#include <string.h>
#include <stdlib.h>

#include "sscanf.h"
#include "args.h"
#include "utils.h"
#include "data.h"
#include "specifiers.h"

extern logprintf_t
	logprintf;

extern int
	gAlpha,
	gForms;

extern E_SSCANF_OPTIONS
	gOptions;

extern unsigned int
	g_iTrueMax,
	g_iInvalid,
	g_iMaxPlayerName;

extern float
	gNameSimilarity;

//extern int
//	g_iServerVersion;

float
	GetSimilarity(char const* string1, char const* string2);

bool
	DoI(char ** input, int * ret)
{
	*ret = GetDec(input);
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1011);
	return false;
}

bool
	DoN(char ** input, int * ret)
{
	*ret = GetNumber(input);
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1012);
	return false;
}

bool
	DoH(char ** input, int * ret)
{
	*ret = GetHex(input);
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1013);
	return false;
}

bool
	DoM(char ** input, unsigned int * ret)
{
	int type;
	// Colours.
	*ret = GetColour(input, &type, gAlpha);
	// Check the given form is allowed.
	if (!(gForms & type))
	{
		SetErrorCode(1001);
		return false;
	}
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1014);
	return false;
}

bool
	DoO(char ** input, int * ret)
{
	*ret = GetOct(input);
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1015);
	return false;
}

bool
	DoF(char ** input, double * ret)
{
	*ret = strtod(*input, input);
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1016);
	return false;
}

bool
	DoC(char ** input, char * ret)
{
	*ret = **input;
	++(*input);
	if (*ret == '\\')
	{
		if (IsSpacer(**input))
		{
			// '\ '
			*ret = **input;
			++(*input);
		}
		else if (**input == '\\')
		{
			// '\\'
			++(*input);
		}
	}
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1017);
	return false;
}

bool
	DoL(char ** input, bool * ret)
{
	*ret = GetLogical(input);
	return true;
}

bool
	DoB(char ** input, int * ret)
{
	*ret = (int)GetBool(input);
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1018);
	return false;
}

bool
	DoG(char ** input, double * ret)
{
	char *
		string = *input;
	int
		temp = 0;
	switch (*string)
	{
	case 'N':
	case 'n':
		if (strichecks(string, "NAN_E"))
		{
			*input += 5;
			temp = FLOAT_NAN_E;
		}
		else if (strichecks(string, "NAN"))
		{
			*input += 3;
			temp = FLOAT_NAN;
		}
		else if (strichecks(string, "NEG_INFINITY"))
		{
			*input += 12;
			temp = FLOAT_NEG_INFINITY;
		}
		else if (strichecks(string, "NEGATIVE_INFINITY"))
		{
			*input += 17;
			temp = FLOAT_NEG_INFINITY;
		}
		*ret = (double)(*((float *)&temp));
		break;
	case 'I':
	case 'i':
		if (strichecks(string, "INFINITY"))
		{
			*input += 8;
			temp = FLOAT_INFINITY;
			*ret = (double)(*((float *)&temp));
		}
		break;
	case '-':
		if (strichecks(string + 1, "INFINITY"))
		{
			*input += 9;
			temp = FLOAT_NEG_INFINITY;
			*ret = (double)(*((float *)&temp));
			break;
		}
		// FALLTHROUGH
	default:
		// Read in the value and save the pointer - may as well use
		// existing, pre set up variables.
		*ret = strtod(string, input);
		break;
	}
	if (GetReturn(input))
	{
		return true;
	}
	SetErrorCode(1019);
	return false;
}

bool
	DoS(char ** input, char ** ret, int length, bool all)
{
	// Don't reallocate the memory, just set pointers to the current data and
	// add nulls to terminate.
	int
		i = 0;
	// Save the pointer to the start of the data.
	*ret = *input;
	// Check if we want the whole remaining string or just the next word.
	char
		* string = *input,
		* outp = string;
	if (all)
	{
		// Everything.
		while (!IsEnd(*string))
		{
			++i;
			// Cap at "length" characters long.
			if (i == length)
			{
				// Could let the loop exit properly as it would next time, but
				// there's no point - it's just extra work and we know it's OK.
				// We set the null before incrementing to ensure it's included
				// in the output.
				SscanfWarning(2, "String buffer overflow.");
				// Removed the break - discard the rest of the string.
				//break;
			}
			else if (i < length)
			{
				if (*string == '\\')
				{
					if (IsEnd(*(string + 1)))
					{
						++string;
						break;
					}
					// You can only escape the escape character or spaces.
					// Spaces aren't technically used here, but they COULD be
					// used in the current string in other circumstances, so
					// they may be present even when not needed.
					if (*(string + 1) == '\\' || IsSpacer(*(string + 1)))
					{
						++string;
					}
				}
				if (outp != string)
				{
					// Compress the string only if we have a discrepancy
					// between input and output pos.
					*outp = *string;
				}
				++outp;
			}
			++string;
		}
	}
	else if (IsDefaultDelimiter())
	{
		while (!IsWhitespace(*string))
		{
			++i;
			if (i == length)
			{
				SscanfWarning(2, "String buffer overflow.");
			}
			else if (i < length)
			{
				if (*string == '\\')
				{
					if (IsEnd(*(string + 1)))
					{
						++string;
						break;
					}
					if (*(string + 1) == '\\' || IsWhitespace(*(string + 1)))
					{
						++string;
					}
				}
				if (outp != string)
				{
					*outp = *string;
				}
				++outp;
			}
			++string;
		}
	}
	else
	{
		// Just a single word.  Note that if your delimiter is a backslash you
		// can't escape it - this is not a bug, just don't try use it as a
		// delimiter and still expect to be able to use it in a string.
		while (!IsEnd(*string) && !IsDelimiter(*string))
		{
			++i;
			if (i == length)
			{
				SscanfWarning(2, "String buffer overflow.");
			}
			else if (i < length)
			{
				if (*string == '\\')
				{
					if (IsEnd(*(string + 1)))
					{
						++string;
						break;
					}
					// Escape spaces, backspace and delimiters - this code is
					// context independent so you can use a string with or
					// without a delimiter and can still escape spaces.
					if (*(string + 1) == '\\' || IsSpacer(*(string + 1)))
					{
						++string;
					}
				}
				if (outp != string)
				{
					*outp = *string;
				}
				++outp;
			}
			++string;
		}
	}
	if (!IsEnd(*string))
	{
		// Skip the final character.
		*input = string + 1;
	}
	else
	{
		// Save the return.
		*input = string;
	}
	// Add a null terminator.
	*outp = '\0';
	// Can't really fail on a string as everything goes!
	return true;
}

bool
	DoU(char ** input, int * ret, unsigned int start)
{
	char
		* end = NULL,
		* string = *input;
	unsigned int
		val = GetUserString(string, &end);
	if (val < g_iTrueMax && IsPlayerConnected(val))
	{
		*input = end;
		*ret = val;
		return false;
	}
	else
	{
		*ret = g_iInvalid;
		int
			* conn = GetConnected();
		size_t
			len = end - string;
		val = start;
		conn += start;
		char
			tmp = *end,
			* name = GetNames();
		name += start * g_iMaxPlayerName;
		*end = '\0';
		if (gNameSimilarity == -1.0f)
		{
			size_t
				best = 1000;
			switch (gOptions & (MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST))
			{
			case MATCH_NAME_FIRST:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !strincmp(name, string, len))
					{
						*ret = val;
						break;
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL | MATCH_NAME_FIRST:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					if (*conn && strstrin(name, string, len))
					{
						*ret = val;
						break;
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case SSCANF_OPTIONS_NONE:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !strincmp(name, string, len))
					{
						if (diff < best)
						{
							*ret = val;
							best = diff;
						}
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && strstrin(name, string, len))
					{
						if (diff < best)
						{
							*ret = val;
							best = diff;
						}
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case CELLMIN_ON_MATCHES:
			case CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Multiple matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !strincmp(name, string, len))
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Both.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && strstrin(name, string, len))
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			}
		}
		else
		{
			float
				best = 0.0f;
			switch (gOptions & (MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST))
			{
			case MATCH_NAME_FIRST:
			case MATCH_NAME_PARTIAL | MATCH_NAME_FIRST:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && GetSimilarity(name, string) >= gNameSimilarity)
					{
						*ret = val;
						break;
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case SSCANF_OPTIONS_NONE:
			case MATCH_NAME_PARTIAL:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					float similarity = GetSimilarity(name, string);
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && similarity >= gNameSimilarity)
					{
						if (similarity > best)
						{
							*ret = val;
							best = similarity;
						}
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case CELLMIN_ON_MATCHES:
			case CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Multiple matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && GetSimilarity(name, string) >= gNameSimilarity)
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					++conn;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			}
		}
		*end = tmp;
	}
	*input = end;
	return true;
}

bool
	DoQ(char ** input, int * ret, unsigned int start)
{
	char
		* end = NULL,
		* string = *input;
	// Get the string.
	unsigned int
		val = GetUserString(string, &end);
	// We only have a less than check here as valid IDs start at 0, and
	// GetUserString can't return numbers less than 0, it's physically
	// impossible for it to do so as there's no code there for it to happen.
	// Check that the player is a valid ID, connected and a non-player
	// character, and if one of those checks FAILS, do the code below.  We
	// could attempt to write a C++ equivalent to foreach here, but this should
	// be pretty fast anyway as it uses direct memory access.
	if (val < g_iTrueMax && IsPlayerConnected(val) && IsPlayerNPC(val))
	{
		*input = end;
		*ret = val;
		return false;
	}
	else
	{
		*ret = g_iInvalid;
		// Find the NPC by name.
		int
			* conn = GetConnected(),
			* npc = GetNPCs();
		size_t
			len = end - string;
		val = start;
		conn += start;
		npc += start;
		// Save the end character for the name.
		char
			tmp = *end,
			* name = GetNames();
		// Make the input string shorter for comparison.
		*end = '\0';
		if (gNameSimilarity == -1.0f)
		{
			size_t
				best = 1000;
			switch (gOptions & (MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST))
			{
			case MATCH_NAME_FIRST:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && !strincmp(name, string, len))
					{
						*ret = val;
						break;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL | MATCH_NAME_FIRST:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && strstrin(name, string, len))
					{
						*ret = val;
						break;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case SSCANF_OPTIONS_NONE:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && !strincmp(name, string, len))
					{
						if (diff < best)
						{
							*ret = val;
							best = diff;
						}
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && strstrin(name, string, len))
					{
						if (diff < best)
						{
							*ret = val;
							best = diff;
						}
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case CELLMIN_ON_MATCHES:
			case CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Multiple matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && !strincmp(name, string, len))
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Both.
				// Loop through all the players and check that they're
				// connected, an NPC, and that their name is correct.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && strstrin(name, string, len))
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					// Can't do *npc++ above as it's not always reached and we
					// need it to be incremented (short circuiting).
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			}
		}
		else
		{
			float
				best = 0.0f;
			switch (gOptions & (MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST))
			{
			case MATCH_NAME_FIRST:
			case MATCH_NAME_PARTIAL | MATCH_NAME_FIRST:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && GetSimilarity(name, string) >= gNameSimilarity)
					{
						*ret = val;
						break;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case SSCANF_OPTIONS_NONE:
			case MATCH_NAME_PARTIAL:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					float similarity = GetSimilarity(name, string);
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && similarity >= gNameSimilarity)
					{
						if (similarity > best)
						{
							*ret = val;
							best = similarity;
						}
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case CELLMIN_ON_MATCHES:
			case CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Multiple matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && *npc && GetSimilarity(name, string) >= gNameSimilarity)
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			}
		}
		*end = tmp;
	}
	// Save the pointer to the end of the name.
	*input = end;
	return true;
}

bool
	DoR(char ** input, int * ret, unsigned int start)
{
	char
		* end = NULL,
		* string = *input;
	unsigned int
		val = GetUserString(string, &end);
	if (val < g_iTrueMax && IsPlayerConnected(val) && !IsPlayerNPC(val))
	{
		*input = end;
		*ret = val;
		return false;
	}
	else
	{
		*ret = g_iInvalid;
		int
			* conn = GetConnected(),
			* npc = GetNPCs();
		size_t
			len = end - string;
		val = start;
		conn += start;
		npc += start;
		char
			tmp = *end,
			* name = GetNames();
		*end = '\0';
		if (gNameSimilarity == -1.0f)
		{
			size_t
				best = 1000;
			switch (gOptions & (MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST))
			{
			case MATCH_NAME_FIRST:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && !strincmp(name, string, len))
					{
						*ret = val;
						break;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL | MATCH_NAME_FIRST:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && strstrin(name, string, len))
					{
						*ret = val;
						break;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case SSCANF_OPTIONS_NONE:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && !strincmp(name, string, len))
					{
						if (diff < best)
						{
							*ret = val;
							best = diff;
						}
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && strstrin(name, string, len))
					{
						if (diff < best)
						{
							*ret = val;
							best = diff;
						}
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case CELLMIN_ON_MATCHES:
			case CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Multiple matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && !strincmp(name, string, len))
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Both.
				// Loop through all the players and check that they're
				// connected, an NPC, and that their name is correct.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && strstrin(name, string, len))
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					// Can't do *npc++ above as it's not always reached and we
					// need it to be incremented (short circuiting).
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			}
		}
		else
		{
			float
				best = 0.0f;
			switch (gOptions & (MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST))
			{
			case MATCH_NAME_FIRST:
			case MATCH_NAME_PARTIAL | MATCH_NAME_FIRST:
				// Original.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && GetSimilarity(name, string) >= gNameSimilarity)
					{
						*ret = val;
						break;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case SSCANF_OPTIONS_NONE:
			case MATCH_NAME_PARTIAL:
				// Partial matches.
				while (val < g_iTrueMax)
				{
					float similarity = GetSimilarity(name, string);
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && similarity >= gNameSimilarity)
					{
						if (similarity > best)
						{
							*ret = val;
							best = similarity;
						}
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			case CELLMIN_ON_MATCHES:
			case CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES:
			case MATCH_NAME_PARTIAL | CELLMIN_ON_MATCHES | MATCH_NAME_FIRST:
				// Multiple matches.
				while (val < g_iTrueMax)
				{
					size_t diff = strlen(name) - len;
					if (*conn && diff >= 0 && !*npc && GetSimilarity(name, string) >= gNameSimilarity)
					{
						if (*ret != g_iInvalid)
						{
							*ret = 0x80000000;
							break;
						}
						*ret = val;
					}
					++conn;
					++npc;
					name += g_iMaxPlayerName;
					++val;
				}
				break;
			}
		}
		*end = tmp;
	}
	*input = end;
	return true;
}

int
	DoID(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	*ret = GetDec(input);
	return GetReturnDefault(input);
}

int
	DoND(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	*ret = GetNumber(input);
	return GetReturnDefault(input);
}

int
	DoHD(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	*ret = GetHex(input);
	return GetReturnDefault(input);
}

int
	DoMD(char ** input, unsigned int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	int type;
	*ret = GetColour(input, &type, gAlpha);
	// Don't check the form is specified in defaults.
	return GetReturnDefault(input);
}

int
	DoOD(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	*ret = GetOct(input);
	return GetReturnDefault(input);
}

int
	DoFD(char ** input, double * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	*ret = strtod(*input, input);
	return GetReturnDefault(input);
}

int
	DoCD(char ** input, char * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	*ret = **input;
	++(*input);
	if (*ret == '\\')
	{
		if (IsSpacer(**input))
		{
			// '\ '
			*ret = **input;
			++(*input);
		}
		else if (**input == '\\')
		{
			// '\\'
			++(*input);
		}
	}
	return GetReturnDefault(input);
}

int
	DoBD(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	*ret = (int)GetBool(input);
	return GetReturnDefault(input);
}

int
	DoGD(char ** input, double * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	char *
		string = *input;
	int
		temp = 0;
	switch (*string)
	{
	case 'N':
	case 'n':
		if (strichecks(string, "NAN_E"))
		{
			*input += 5;
			temp = FLOAT_NAN_E;
		}
		else if (strichecks(string, "NAN"))
		{
			*input += 3;
			temp = FLOAT_NAN;
		}
		else if (strichecks(string, "NEG_INFINITY"))
		{
			*input += 12;
			temp = FLOAT_NEG_INFINITY;
		}
		else if (strichecks(string, "NEGATIVE_INFINITY"))
		{
			*input += 17;
			temp = FLOAT_NEG_INFINITY;
		}
		*ret = (double)(*((float *)&temp));
		break;
	case 'I':
	case 'i':
		if (strichecks(string, "INFINITY"))
		{
			*input += 8;
			temp = FLOAT_INFINITY;
			*ret = (double)(*((float *)&temp));
		}
		break;
	case '-':
		if (strichecks(string + 1, "INFINITY"))
		{
			*input += 9;
			temp = FLOAT_NEG_INFINITY;
			*ret = (double)(*((float *)&temp));
			break;
		}
		// FALLTHROUGH
	default:
		// Read in the value and save the pointer - may as well use
		// existing, pre set up variables.
		*ret = strtod(string, input);
		break;
	}
	return GetReturnDefault(input);
}

int
	DoSD(char ** input, char ** ret, int * length, struct args_s & args)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		SscanfError(64, "(*) is not supported in strings/arrays yet.");
		args.Next();
	}
	// Don't reallocate the memory, just set pointers to the current data and
	// add nulls to terminate.
	int
		i = 0;
	// Save the pointer to the start of the data.
	*ret = *input;
	// Check if we want the whole remaining string or just the next word.
	char
		* string = *input,
		* outp = string;
	while (!IsEnd(*string) && !IsDelimiter(*string))
	{
		++i;
		if (*string == '\\')
		{
			if (IsEnd(*(string + 1)))
			{
				++string;
				break;
			}
			// Escape spaces, backspace and delimiters - this code is
			// context independent so you can use a string with or
			// without a delimiter and can still escape spaces.
			if (*(string + 1) == '\\' || IsWhitespace(*(string + 1)) || IsDelimiter(*(string + 1)))
			{
				++string;
			}
		}
		if (outp != string)
		{
			*outp = *string;
		}
		++outp;
		++string;
	}
	if (IsDelimiter(*string))
	{
		// Skip the final character.
		*input = string + 1;
		// NOW get the length.
		*length = GetLength(input, args);
	}
	else
	{
		SscanfWarning(11, "Unclosed default value.");
		// Save the return.
		*input = string;
		SscanfError(19, "String/array must include a length, please add a destination size.");
		*length = 1;
	}
	// Add a null terminator.
	if (i >= *length)
	{
		SscanfWarning(2, "String buffer overflow.");
		*(*ret + *length - 1) = '\0';
	}
	else
	{
		*outp = '\0';
	}
	return 1;
}

int
	DoUD(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	if (gOptions & OLD_DEFAULT_NAME) DoU(input, ret, 0);
	else DoN(input, ret);
	return GetReturnDefault(input);
}

int
	DoQD(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	if (gOptions & OLD_DEFAULT_NAME) DoQ(input, ret, 0);
	else DoN(input, ret);
	return GetReturnDefault(input);
}

int
	DoRD(char ** input, int * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	if (gOptions & OLD_DEFAULT_NAME) DoR(input, ret, 0);
	else DoN(input, ret);
	return GetReturnDefault(input);
}

int
	DoLD(char ** input, bool * ret)
{
	if (!FindDefaultStart(input))
	{
		return 0;
	}
	if (**input == '*')
	{
		++(*input);
		return -GetReturnDefault(input);
	}
	DoL(input, ret);
	return GetReturnDefault(input);
}


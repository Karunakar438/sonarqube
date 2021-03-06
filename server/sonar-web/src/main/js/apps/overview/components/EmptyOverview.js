/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
// @flow
import React from 'react';
import { Link } from 'react-router';
import { translate } from '../../../helpers/l10n';

type Props = {
  component: { key: string }
};

export default function EmptyOverview({ component }: Props) {
  const rawMessage = translate('provisioning.no_analysis.delete');
  const head = rawMessage.substr(0, rawMessage.indexOf('{0}'));
  const tail = rawMessage.substr(rawMessage.indexOf('{0}') + 3);

  return (
    <div className="page page-limited">
      <div className="alert alert-warning">
        {translate('provisioning.no_analysis')}
      </div>

      <div className="big-spacer-top">
        {head}
        <Link
          className="text-danger"
          to={{ pathname: '/project/deletion', query: { id: component.key } }}>
          {translate('provisioning.no_analysis.delete_it')}
        </Link>
        {tail}
      </div>

      <div className="big-spacer-top">
        <h4>{translate('key')}</h4>
        <code>{component.key}</code>
      </div>
    </div>
  );
}

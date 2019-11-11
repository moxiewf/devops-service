import React, { createContext, useContext, useEffect, useMemo } from 'react';
import { inject } from 'mobx-react';
import { injectIntl } from 'react-intl';
import { DataSet } from 'choerodon-ui/pro';
import AllProjectDataSet from './AllProjectDataSet';
import PermissionProjectDataSet from './PermissionProjectDataSet';
import DetailDataSet from './DetailDataSet';
import OptionsDataSet from './OptionsDataSet';

const Store = createContext();

export function usePVPermissionStore() {
  return useContext(Store);
}

export const StoreProvider = injectIntl(inject('AppState')(
  (props) => {
    const {
      AppState: { currentMenuType: { projectId } },
      intl: { formatMessage },
      children,
      intlPrefix,
      pvId,
    } = props;

    const DetailDs = useMemo(() => new DataSet(DetailDataSet(intlPrefix, formatMessage, projectId, pvId)), [projectId, pvId]);
    const optionDs = useMemo(() => new DataSet(OptionsDataSet(projectId, pvId)), [projectId, pvId]);
    const allProjectDs = useMemo(() => new DataSet(AllProjectDataSet(intlPrefix, formatMessage, projectId, DetailDs)), [projectId]);
    const permissionProjectDs = useMemo(() => new DataSet(PermissionProjectDataSet(intlPrefix, formatMessage, projectId, pvId, optionDs, DetailDs)), [projectId, pvId]);

    useEffect(() => {
      loadData();
    }, []);

    async function loadData() {
      await DetailDs.query();
      allProjectDs.query();
      permissionProjectDs.query();
    }

    const value = {
      ...props,
      allProjectDs,
      permissionProjectDs,
      DetailDs,
    };
    return (
      <Store.Provider value={value}>
        {children}
      </Store.Provider>
    );
  },
));